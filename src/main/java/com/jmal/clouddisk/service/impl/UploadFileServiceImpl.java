package com.jmal.clouddisk.service.impl;

import cn.hutool.core.io.FileUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.AuthInterceptor;
import com.jmal.clouddisk.common.exception.CommonException;
import com.jmal.clouddisk.common.exception.ExceptionType;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.UploadApiParam;
import com.jmal.clouddisk.model.UploadResponse;
import com.jmal.clouddisk.service.IUploadFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.*;
import com.mongodb.client.AggregateIterable;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.tasks.UnsupportedFormatException;
import org.bson.BsonNull;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.*;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.*;
import static java.util.stream.Collectors.toList;

/**
 * @Description 文件管理
 * @Author jmal
 * @Date 2020-01-14 13:05
 * @blame jmal
 */
@Service
@Slf4j
public class UploadFileServiceImpl implements IUploadFileService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    IUserService userService;

    private static final String COLLECTION_NAME = "fileDocument";

    private static final String CONTENT_TYPE_IMAGE = "image";

    @Value("${root-path}")
    String rootPath;

    private Cache<String, List<Integer>> resumeCache = CaffeineUtil.getResumeCache();


    /***
     * 文件列表
     * @param upload
     * @param pageIndex
     * @param pageSize
     * @return
     */
    @Override
    public ResponseResult listFiles(UploadApiParam upload, int pageIndex, int pageSize) throws CommonException {
        String userId = upload.getUserId();
        if (StringUtils.isEmpty(userId)) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg() + "userId");
        }
        String currentDirectory = getUserDirectory(upload.getCurrentDirectory());
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("path").is(currentDirectory));
        long skip = (pageIndex - 1) * pageSize;
        query.skip(skip);
        query.limit(pageSize);
        List<FileDocument> list = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        long now = System.currentTimeMillis();

        list = list.parallelStream().peek(fileDocument -> {
            LocalDateTime updateDate = fileDocument.getUpdateDate();
            long update = TimeUntils.getMilli(updateDate);
            fileDocument.setAgoTime(now - update);
            if (fileDocument.getIsFolder()) {
                String path = fileDocument.getPath() + fileDocument.getName();
                long size = getFolderSize(fileDocument.getUserId(), path);
                fileDocument.setSize(size);
            }
        }).collect(toList());

        // 按文件名排序
        list.sort(this::compareByFileName);

        return ResultUtil.success(list);
    }


    /***
     * 根据文件名排序
     * @param f1
     * @param f2
     * @return
     */
    private int compareByFileName(FileDocument f1, FileDocument f2) {
        if (f1.getIsFolder() && !f2.getIsFolder()) {
            return -1;
        } else if (f1.getIsFolder() && f2.getIsFolder()) {
            return compareByName(f1, f2);
        } else if (!f1.getIsFolder() && f2.getIsFolder()) {
            return 1;
        } else {
            return compareByName(f1, f2);
        }
    }

    private int compareByName(FileDocument f1, FileDocument f2) {
        Comparator<Object> cmp = Collator.getInstance(java.util.Locale.CHINA);
        return cmp.compare(f1.getName(), f2.getName());
    }

    /***
     * 统计文件夹的大小
     * @return
     */
    private long getFolderSize(String userId, String path) {
        List<Bson> list = Arrays.asList(
                match(and(eq("userId", userId),
                        eq("isFolder", false), regex("path", "^"+path))),
                group(new BsonNull(), sum("totalSize", "$size")));
        AggregateIterable<Document> aggregate = mongoTemplate.getCollection(COLLECTION_NAME).aggregate(list);
        long totalSize = 0;
        Document doc = aggregate.first();
        if (doc != null) {
            Object object = doc.get("totalSize");
            if (object != null) {
                totalSize = Long.parseLong(object.toString());
            }
        }
        return totalSize;
    }

    /**
     * 查询附件
     *
     * @param id       文件id
     * @param username
     * @return
     * @throws IOException
     */
    @Override
    public Optional<FileDocument> getById(String id, String username) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            setContent(username, fileDocument);
            return Optional.of(fileDocument);
        }
        return Optional.empty();
    }

    private void setContent(String username, FileDocument fileDocument) {
        String currentDirectory = getUserDirectory(fileDocument.getPath());
        File file = new File(rootPath + File.separator + username + currentDirectory + fileDocument.getName());
        byte[] content = FileUtil.readBytes(file);
        fileDocument.setContent(content);
    }

    /***
     * 查看缩略图
     * @param id
     * @param username
     * @return
     */
    @Override
    public Optional<FileDocument> thumbnail(String id, String username) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            if (fileDocument.getContent() != null) {
                return Optional.of(fileDocument);
            } else {
                setContent(username, fileDocument);
                return Optional.of(fileDocument);
            }
        }
        return Optional.empty();
    }

    /***
     * 获取文件信息
     * @param fileIds
     * @param username
     * @return
     */
    private FileDocument getFileInfo(List<String> fileIds, String username) throws UnsupportedEncodingException {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        List<FileDocument> fileDocuments = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        int size = fileDocuments.size();
        if (size > 0) {
            FileDocument fileDocument = fileDocuments.get(0);
            String currentDirectory = getUserDirectory(fileDocument.getPath());
            String startPath = File.separator + username + currentDirectory;
            String filename = fileDocument.getName();
            if (size > 1) {
                filename = "download";
            }
            String filePath = startPath + filename;
            if (size == 1 && !fileDocument.getIsFolder()) {
                // 单个文件
                fileDocument.setPath(filePath);
                return fileDocument;
            } else {
                fileDocument.setIsFolder(true);
                // 压缩文件夹
                String zipFilePath = filePath + ".zip";
                String zipFilename = filename + ".zip";

                List<Bson> selectFolders = new ArrayList<>();
                for (FileDocument document : fileDocuments) {
                    selectFolders.add(regex("path", "^"+document.getPath() + document.getName()));
                }

                List<String> selectFiles = new ArrayList<>();
                for (FileDocument document : fileDocuments) {
                    selectFiles.add(document.getName());
                }

                String parentPath = fileDocument.getPath();

                List<Bson> list = Arrays.asList(
                        match(and(eq("userId", fileDocument.getUserId()),
                                regex("path", "^"+parentPath))),
                        match(or(Arrays.asList(
                                or(selectFolders),
                                in("name", selectFiles)))));
                AggregateIterable<Document> aggregate = mongoTemplate.getCollection(COLLECTION_NAME).aggregate(list);

                String temp = "/download/";
                StringBuilder res = new StringBuilder();
                for (Document doc : aggregate) {
                    if (doc != null) {
                        boolean isFolder = doc.getBoolean("isFolder");
                        if (!isFolder) {
                            String relativePath = doc.getString("path");
                            String relativeFileName = doc.getString("name");
                            long fileSize = doc.getLong("size");
                            res.append(String.format("%s %d %s %s\n", "-", fileSize, URLEncoder.encode(File.separator + username + relativePath + relativeFileName, "UTF-8"), temp + relativePath.substring(parentPath.length()) + relativeFileName));
                            System.out.println(String.format("%s %d %s %s\n", "-", fileSize, File.separator + username + relativePath + relativeFileName, temp + relativePath.substring(parentPath.length()) + relativeFileName));
                        }
                    }
                }
                fileDocument.setPath(zipFilePath);
                fileDocument.setName(zipFilename);
                fileDocument.setContent(res.toString().getBytes(StandardCharsets.UTF_8));
                return fileDocument;
            }

        }
        return null;
    }

    /***
     * 交给nginx处理
     * @param request
     * @param response
     * @param fileIds
     * @param isDownload
     * @throws IOException
     */
    @Override
    public void nginx(HttpServletRequest request, HttpServletResponse response, List<String> fileIds, boolean isDownload) throws IOException {
        String username = userService.getUserName(request.getParameter(AuthInterceptor.JMAL_TOKEN));
        FileDocument fileDocument = getFileInfo(fileIds, username);
        if(fileDocument != null){
            String filename = fileDocument.getName();
            String path = fileDocument.getPath();
            //获取浏览器名（IE/Chrome/firefox）目前主流的四大浏览器内核Trident(IE)、Gecko(Firefox内核)、WebKit(Safari内核,Chrome内核原型,开源)以及Presto(Opera前内核) (已废弃)
            String gecko = "Gecko", webKit = "WebKit";
            String userAgent = request.getHeader("User-Agent");
            if (userAgent.contains(gecko) || userAgent.contains(webKit)) {
                path = new String(path.getBytes(StandardCharsets.UTF_8), "ISO8859-1");
                filename = new String(filename.getBytes(StandardCharsets.UTF_8), "ISO8859-1");
            } else {
                path = URLEncoder.encode(path, "UTF-8");
                filename = URLEncoder.encode(filename, "UTF-8");
            }
            response.setHeader("Content-Type", fileDocument.getContentType());
            response.setHeader("X-Accel-Charset", "utf-8");
            if(fileDocument.getIsFolder()){
                response.setHeader("Content-Disposition", "attachment; filename=test.zip");
                response.setHeader("X-Archive-Files", "zip");
                response.setHeader("X-Archive-Charset", "utf-8");
            }else{
                response.setHeader("X-Accel-Redirect", path);
            }
            if (isDownload) {
                response.setHeader("Content-Disposition", "attachment;filename=\"" + filename + "\"");
                OutputStream out = response.getOutputStream();
                if(fileDocument.getContent() != null){
                    out.write(fileDocument.getContent());
                }
                out.flush();
            }
        }
    }

    /***
     * 重命名
     * @param username
     * @param id
     * @return
     */
    @Override
    public ResponseResult rename(String newFileName, String username, String id) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            String currentDirectory = getUserDirectory(fileDocument.getPath());
            String filePath = rootPath + File.separator + username + currentDirectory;
            File file = new File(filePath + fileDocument.getName());
            if(file.renameTo(new File(filePath + newFileName))){
                Query query = new Query();
                query.addCriteria(Criteria.where("_id").is(id));
                Update update = new Update();
                update.set("name",newFileName);
                mongoTemplate.upsert(query,update,COLLECTION_NAME);
            } else {
                return ResultUtil.error("重命名失败");
            }
            return ResultUtil.success(true);
        } else {
            return ResultUtil.error("数据库查询失败");
        }
    }

    /***
     * 保存文件(分片)
     * @param upload
     * @return
     */
    @Override
    public ResponseResult upload(UploadApiParam upload) throws IOException {
        UploadResponse uploadResponse = new UploadResponse();
        int currentChunkSize = upload.getCurrentChunkSize();
        long totalSize = upload.getTotalSize();
        String filename = upload.getFilename();
        String md5 = upload.getIdentifier();
        MultipartFile file = upload.getFile();
        //用户磁盘目录
        String userDirectoryFilePath = getUserDirectoryFilePath(upload);
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        if (currentChunkSize == totalSize) {
            //没有分片,直接存
            File chunkFile = new File(upload.getRootPath() + File.separator + upload.getUsername() + userDirectoryFilePath);
            FileUtil.writeFromStream(file.getInputStream(), chunkFile);
            //保存文件信息
            upload.setInputStream(file.getInputStream());
            upload.setContentType(file.getContentType());
            upload.setSuffix(FileUtil.extName(filename));
            saveFileInfo(upload, md5, date);
            uploadResponse.setUpload(true);
        } else {
            // 多个分片
            // 落地保存文件
            // 这时保存的每个块, 块先存好, 后续会调合并接口, 将所有块合成一个大文件
            // 保存在用户的tmp目录下
            File chunkFile = new File(upload.getRootPath() + File.separator + upload.getUsername() + File.separator + "tmp" + File.separator + md5 + File.separator + upload.getChunkNumber());
            FileUtil.writeFromStream(file.getInputStream(), chunkFile);
            uploadResponse.setUpload(true);
            setResumeCache(upload);
            // 检测是否已经上传完了所有分片,上传完了则需要合并
            if (checkIsNeedMerge(upload)) {
                uploadResponse.setMerge(true);
            }

        }
        return ResultUtil.success(uploadResponse);
    }

    /***
     * 上传文件夹
     * @param upload
     * @return
     */
    @Override
    public ResponseResult uploadFolder(UploadApiParam upload) {
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        saveFolderInfo(upload, date);
        return ResultUtil.success();
    }

    /***
     * 保存文件信息
     * @param upload
     * @param md5
     * @param date
     */
    private void saveFileInfo(UploadApiParam upload, String md5, LocalDateTime date) throws IOException {
        String contentType = upload.getContentType();
        FileDocument fileDocument = new FileDocument();
        String filename = upload.getFilename();
        String currentDirectory = getUserDirectory(upload.getCurrentDirectory());
        String relativePath = upload.getRelativePath();
        relativePath = relativePath.substring(0, relativePath.length() - filename.length());
        fileDocument.setPath(currentDirectory + relativePath);
        fileDocument.setSize(upload.getTotalSize());
        fileDocument.setContentType(contentType);
        fileDocument.setMd5(md5);
        fileDocument.setName(filename);
        fileDocument.setIsFolder(upload.getIsFolder());
        fileDocument.setUploadDate(date);
        fileDocument.setUpdateDate(date);
        fileDocument.setSuffix(upload.getSuffix());
        fileDocument.setUserId(upload.getUserId());
        if (contentType.startsWith(CONTENT_TYPE_IMAGE)) {
            // 生成缩略图
            Thumbnails.Builder<? extends InputStream> thumbnail = Thumbnails.of(upload.getInputStream());
            thumbnail.size(100, 100);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                thumbnail.toOutputStream(out);
                fileDocument.setContent(out.toByteArray());
            } catch (UnsupportedFormatException e) {
                log.warn(e.getMessage());
            }
        }
        mongoTemplate.save(fileDocument, COLLECTION_NAME);
    }

    /***
     * 保存文件夹信息
     * @param upload
     * @param date
     */
    private void saveFolderInfo(UploadApiParam upload, LocalDateTime date) {
        String userId = upload.getUserId();
        String folderPath = upload.getFolderPath();
        String path = getUserDirectory(upload.getCurrentDirectory());
        if (!StringUtils.isEmpty(folderPath)) {
            path += folderPath;
        }
        String folderName = upload.getFilename();
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("isFolder").is(true));
        query.addCriteria(Criteria.where("path").is(path));
        query.addCriteria(Criteria.where("name").is(folderName));
        Update update = new Update();
        update.set("userId", userId);
        update.set("isFolder", true);
        update.set("path", path);
        update.set("pathname", folderName);
        update.set("name", folderName);
        update.set("uploadDate", date);
        update.set("updateDate", date);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
    }

    private void setResumeCache(UploadApiParam upload) {
        int chunkNumber = upload.getChunkNumber();
        String md5 = upload.getIdentifier();
        List<Integer> chunks = resumeCache.get(md5, key -> createResumeCache(upload));
        if (chunks != null) {
            chunks.add(chunkNumber);
            resumeCache.put(md5, chunks);
        }
    }

    /***
     * 获取已经保存的分片索引
     */
    private List<Integer> getSavedChunk(UploadApiParam upload){
        String md5 = upload.getIdentifier();
        return resumeCache.get(md5, key -> createResumeCache(upload));
    }

    /***
     * 检测是否需要合并
     */
    private boolean checkIsNeedMerge(UploadApiParam upload){
        int totalChunks = upload.getTotalChunks();
        List<Integer> chunkList = getSavedChunk(upload);
        return totalChunks == chunkList.size();
    }

    /***
     * 读取分片文件是否存在
     * @return
     */
    private List<Integer> createResumeCache(UploadApiParam upload) {
        List<Integer> resumeList = new ArrayList<>();
        String md5 = upload.getIdentifier();
        // 读取tmp分片目录所有文件
        File f = new File(upload.getRootPath() + File.separator + upload.getUsername() + File.separator + "tmp" + File.separator + md5);
        if (f.exists()) {
            // 排除目录，只要文件
            File[] fileArray = f.listFiles(pathName -> !pathName.isDirectory());
            if (fileArray != null) {
                if (fileArray.length > 0) {
                    for (File file : fileArray) {
                        // 分片文件
                        int resume = Integer.parseInt(file.getName());
                        resumeList.add(resume);
                    }
                }
            }
        }
        return resumeList;
    }

    /***
     * 检查文件/分片是否存在
     * @param upload
     * @return
     */
    @Override
    public ResponseResult checkChunkUploaded(UploadApiParam upload) throws IOException {
        UploadResponse uploadResponse = new UploadResponse();
        String md5 = upload.getIdentifier();
        String path = getUserDirectory(upload.getCurrentDirectory());

        String relativePath = upload.getRelativePath();
        path += relativePath.substring(0, relativePath.length() - upload.getFilename().length());
        FileDocument fileDocument = getByMd5(path, upload.getUserId(), md5);
        if (fileDocument != null) {
            // 文件已存在
            uploadResponse.setPass(true);
        } else {
            int totalChunks = upload.getTotalChunks();
            List<Integer> chunks = resumeCache.get(md5, key -> createResumeCache(upload));
            // 返回已存在的分片
            uploadResponse.setResume(chunks);
            assert chunks != null;
            if(totalChunks == chunks.size()){
                // 文件不存在,并且已经上传了所有的分片,则合并保存文件
                merge(upload);
            }
        }
        uploadResponse.setUpload(true);
        return ResultUtil.success(uploadResponse);
    }

    /***
     * 合并文件
     * @param upload
     * @return
     */
    @Override
    public ResponseResult merge(UploadApiParam upload) throws IOException {
        UploadResponse uploadResponse = new UploadResponse();
        String md5 = upload.getIdentifier();
        String filename = upload.getFilename();
        // 用户磁盘目录
        String userDirectoryFilePath = getUserDirectoryFilePath(upload);

        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);

        // 读取目录所有文件
        File f = new File(upload.getRootPath() + File.separator + upload.getUsername() + File.separator + "tmp" + File.separator + md5);
        // 排除目录，只要文件
        File[] fileArray = f.listFiles(pathName -> !pathName.isDirectory());

        // 转成集合，便于排序
        List<File> fileList = new ArrayList<>(Arrays.asList(Objects.requireNonNull(fileArray)));

        // 从小到大排序
        fileList.sort(UploadFileServiceImpl::compare);

        //fileName：沿用原始的文件名，或者可以使用随机的字符串作为新文件名，但是要 保留原文件的后缀类型
        File outputFile = new File(upload.getRootPath() + File.separator + upload.getUsername() + userDirectoryFilePath);

        File parentFile = outputFile.getParentFile();
        if (!parentFile.exists()) {
            if (!parentFile.mkdirs()) {
                throw new CommonException(-1, "创建文件夹失败");
            }
        }
        // 创建文件
        if (!outputFile.exists()) {
            if (!outputFile.createNewFile()) {
                throw new CommonException(-1, "创建文件失败");
            }
        }
        // 输出流
        FileOutputStream fileOutputStream = new FileOutputStream(outputFile);
        FileChannel outChannel = fileOutputStream.getChannel();
        // 合并，核心就是FileChannel，将多个文件合并为一个文件
        FileChannel inChannel;
        for (File file : fileList) {
            inChannel = new FileInputStream(file).getChannel();
            inChannel.transferTo(0, inChannel.size(), outChannel);
            inChannel.close();
            // 删除分片
            if (!file.delete()) {
                throw new CommonException(-1, "删除分片失败");
            }
        }
        // 关闭流
        fileOutputStream.close();
        outChannel.close();
        // 清除文件夹
        if (f.isDirectory() && f.exists()) {
            if (!f.delete()) {
                throw new CommonException(-1, "清除文件失败");
            }
        }
        //保存文件信息
        upload.setInputStream(FileUtil.getInputStream(outputFile));
        String extName = FileUtil.extName(filename);
        upload.setSuffix(extName);
        upload.setContentType(FileContentTypeUtils.getContentType(extName));
        saveFileInfo(upload, md5, date);
        //清除缓存
        resumeCache.invalidate(md5);
        uploadResponse.setUpload(true);
        return ResultUtil.success(uploadResponse);
    }

    private static int compare(File o1, File o2) {
        if (Integer.parseInt(o1.getName()) < Integer.parseInt(o2.getName())) {
            return -1;
        }
        return 1;
    }

    /***
     * 用户磁盘目录
     * @param upload
     * @return String
     */
    private String getUserDirectoryFilePath(UploadApiParam upload) {
        String currentDirectory = upload.getCurrentDirectory();
        if (StringUtils.isEmpty(currentDirectory)) {
            currentDirectory = "/";
        }
        if (upload.getIsFolder()) {
            currentDirectory += upload.getRelativePath();
        } else {
            currentDirectory += "/" + upload.getRelativePath();
        }
        currentDirectory = currentDirectory.replaceAll("/", File.separator);
        return currentDirectory;
    }

    private String getUserDirectory(String currentDirectory) {
        String mongodbEndPath = "/";
        if (StringUtils.isEmpty(currentDirectory)) {
            currentDirectory = "/";
        } else {
            if (!currentDirectory.endsWith(mongodbEndPath)) {
                currentDirectory += "/";
            }
        }
        currentDirectory = currentDirectory.replaceAll("/", File.separator);
        return currentDirectory;
    }

    /***
     * 收藏文件或文件夹
     * @param fileId
     * @return
     * @throws CommonException
     */
    @Override
    public ResponseResult favorite(String fileId) throws CommonException {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update();
        update.set("isFavorite", true);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 取消收藏
     * @param fileId
     * @return
     */
    @Override
    public ResponseResult unFavorite(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update();
        update.set("isFavorite", false);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 删除文件
     * @param fileIds
     * @return
     */
    @Override
    public ResponseResult delete(String username, List<String> fileIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        List<FileDocument> fileDocuments = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        boolean isDel = false;
        for (FileDocument fileDocument : fileDocuments) {
            String currentDirectory = getUserDirectory(fileDocument.getPath());
            String filePath = rootPath + File.separator + username + currentDirectory + fileDocument.getName();
            File file = new File(filePath);
            isDel = FileUtil.del(file);
            if (fileDocument.getIsFolder()) {
                // 删除文件夹及其下的所有文件
                Query query1 = new Query();
                query1.addCriteria(Criteria.where("path").regex("^"+fileDocument.getPath() + fileDocument.getName()));
                mongoTemplate.remove(query1, COLLECTION_NAME);
                isDel = true;
            }
        }
        if (isDel) {
            mongoTemplate.remove(query, COLLECTION_NAME);
        } else {
            throw new CommonException(-1, "删除失败");
        }
        return ResultUtil.success();
    }

    /***
     * 是否存在该文件
     * @param userId
     * @param md5
     * @return
     */
    private FileDocument getByMd5(String path, String userId, String md5) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("md5").is(md5));
        query.addCriteria(Criteria.where("path").is(path));
        return mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
    }
}

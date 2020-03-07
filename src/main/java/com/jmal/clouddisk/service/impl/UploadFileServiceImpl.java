package com.jmal.clouddisk.service.impl;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;
import static com.mongodb.client.model.Filters.or;
import static com.mongodb.client.model.Filters.regex;
import static java.util.stream.Collectors.toList;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.jmal.clouddisk.model.User;
import com.jmal.clouddisk.util.*;
import org.bson.BsonNull;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.AuthInterceptor;
import com.jmal.clouddisk.common.exception.CommonException;
import com.jmal.clouddisk.common.exception.ExceptionType;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.UploadApiParam;
import com.jmal.clouddisk.model.UploadResponse;
import com.jmal.clouddisk.service.IUploadFileService;
import com.jmal.clouddisk.service.IUserService;
import com.mongodb.client.AggregateIterable;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.tasks.UnsupportedFormatException;

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

    /***
     * 文件路径分隔符,mongodb里专用
     */
    private static final String DIR_SEPARATOR = "/";

    /***
     * 保存分片的目录
     */
    private static final String TEMPORARY_DIRECTORY = "temporary directory";

    private static final String COLLECTION_NAME = "fileDocument";

    private static final String CONTENT_TYPE_IMAGE = "image";
    private static final String CONTENT_TYPE_MARK_DOWN = "text/markdown";

    /***
     * 前端文件夹树的第一级的文件Id
     */
    private static final String FIRST_FILE_TREE_ID = "0";

    @Value("${root-path}")
    String rootPath;

    private Cache<String, CopyOnWriteArrayList<Integer>> resumeCache = CaffeineUtil.getResumeCache();


    /***
     * 文件列表
     * @param upload
     * @return
     */
    @Override
    public ResponseResult<Object> listFiles(UploadApiParam upload) throws CommonException {
        ResponseResult<Object> result = ResultUtil.genResult();
        String currentDirectory = getUserDirectory(upload.getCurrentDirectory());
        List<FileDocument> list = getFileDocuments(upload, Criteria.where("path").is(currentDirectory));
        result.setData(list);
        result.setCount(getFileDocumentsCount(upload, Criteria.where("path").is(currentDirectory)));
        return result;
    }

    private long getFileDocumentsCount(UploadApiParam upload, Criteria... criteriaList) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(upload.getUserId()));
        for (Criteria criteria : criteriaList) {
            query.addCriteria(criteria);
        }
        return mongoTemplate.count(query, COLLECTION_NAME);
    }

    private List<FileDocument> getFileDocuments(UploadApiParam upload, Criteria... criteriaList) {
        Query query = getQuery(upload, criteriaList);
        Integer pageSize = upload.getPageSize(), pageIndex = upload.getPageIndex();
        if (pageSize != null && pageIndex != null) {
            long skip = (pageIndex - 1) * pageSize;
            query.skip(skip);
            query.limit(pageSize);
        }
        query.with(new Sort(Sort.Direction.DESC, "isFolder"));
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
        return list;
    }

    private List<FileDocument> getDirDocuments(UploadApiParam upload, Criteria... criteriaList) {
        Query query = getQuery(upload, criteriaList);
        query.addCriteria(Criteria.where("isFolder").is(true));
        List<FileDocument> list = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        // 按文件名排序
        list.sort(this::compareByFileName);
        return list;
    }

    private Query getQuery(UploadApiParam upload, Criteria[] criteriaList) {
        String userId = upload.getUserId();
        if (StringUtils.isEmpty(userId)) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg() + "userId");
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        for (Criteria criteria : criteriaList) {
            query.addCriteria(criteria);
        }
        return query;
    }

    @Override
    public ResponseResult<Object> searchFile(UploadApiParam upload, String keyword) throws CommonException {
        ResponseResult<Object> result = ResultUtil.genResult();
        Criteria criteria1 = Criteria.where("name").regex(keyword);
        return getCountResponseResult(upload, result, criteria1);
    }

    private ResponseResult<Object> getCountResponseResult(UploadApiParam upload, ResponseResult<Object> result, Criteria... criteriaList) {
        List<FileDocument> list = getFileDocuments(upload, criteriaList);
        result.setData(list);
        result.setCount(getFileDocumentsCount(upload, criteriaList));
        return result;
    }

    @Override
    public ResponseResult<Object> searchFileAndOpenDir(UploadApiParam upload, String id) throws CommonException {
        ResponseResult<Object> result = ResultUtil.genResult();
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        assert fileDocument != null;
        String currentDirectory = getUserDirectory(fileDocument.getPath() + fileDocument.getName());
        Criteria criteria = Criteria.where("path").is(currentDirectory);
        return getCountResponseResult(upload, result, criteria);
    }

    private FileDocument getFileDocumentById(String fileId) {
        if (StringUtils.isEmpty(fileId) || FIRST_FILE_TREE_ID.equals(fileId)) {
            return null;
        }
        return mongoTemplate.findById(fileId, FileDocument.class, COLLECTION_NAME);
    }

    private String getRelativePathByFileId(FileDocument fileDocument) {
        if (fileDocument == null) {
            return getUserDirectory(null);
        }
        if (fileDocument.getIsFolder()) {
            return getUserDirectory(fileDocument.getPath() + fileDocument.getName());
        }
        String currentDirectory = fileDocument.getPath() + fileDocument.getName();
        return currentDirectory.replaceAll(DIR_SEPARATOR, File.separator);
    }

    private String getUserDir(String userName) {
        return rootPath + File.separator + userName;
    }


    /***
     * 查找下级目录
     * @param upload
     * @param fileId
     * @return
     */
    @Override
    public ResponseResult<Object> queryFileTree(UploadApiParam upload, String fileId) {
        String currentDirectory = getUserDirectory(null);
        if (!StringUtils.isEmpty(fileId)) {
            FileDocument fileDocument = mongoTemplate.findById(fileId, FileDocument.class, COLLECTION_NAME);
            assert fileDocument != null;
            currentDirectory = getUserDirectory(fileDocument.getPath() + fileDocument.getName());
        }
        Criteria criteria = Criteria.where("path").is(currentDirectory);
        List<FileDocument> list = getDirDocuments(upload, criteria);
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
                        eq("isFolder", false), regex("path", "^" + path))),
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
            if (fileDocument.getContent() == null) {
                setContent(username, fileDocument);
            }
            return Optional.of(fileDocument);
        }
        return Optional.empty();
    }

    /***
     * 获取文件信息
     * @param fileIds
     * @param username
     * @return
     */
    private FileDocument getFileInfo(List<String> fileIds, String username) throws CommonException{
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
                    selectFolders.add(regex("path", "^" + document.getPath() + document.getName()));
                }

                List<String> selectFiles = new ArrayList<>();
                for (FileDocument document : fileDocuments) {
                    selectFiles.add(document.getName());
                }

                String parentPath = fileDocument.getPath();

                List<Bson> list = Arrays.asList(
                        match(and(eq("userId", fileDocument.getUserId()),
                                regex("path", "^" + parentPath))),
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
                            try {
                                res.append(String.format("%s %d %s %s\n", "-", fileSize, URLEncoder.encode(File.separator + username + relativePath + relativeFileName, "UTF-8"), temp + relativePath.substring(parentPath.length()) + relativeFileName));
                            } catch (UnsupportedEncodingException e) {
                                throw new CommonException(-1,e.getMessage());
                            }
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
     * 交给nginx处理(共有的,任何人都和访问)
     * @param request
     * @param response
     * @param fileIds
     * @param isDownload
     * @throws IOException
     */
    @Override
    public void publicNginx(HttpServletRequest request, HttpServletResponse response, List<String> fileIds, boolean isDownload) throws CommonException {
        FileDocument f = mongoTemplate.findById(fileIds.get(0),FileDocument.class, COLLECTION_NAME);
        if(f != null){
            User user = userService.userInfoById(f.getUserId());
            FileDocument fileDocument = getFileInfo(fileIds, user.getUsername());
            try {
                nginx(request, response, isDownload, fileDocument);
            } catch (IOException e) {
                throw new CommonException(-1,e.getMessage());
            }
        }
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
    public void nginx(HttpServletRequest request, HttpServletResponse response, List<String> fileIds, boolean isDownload) throws CommonException {
        String username = userService.getUserName(request.getParameter(AuthInterceptor.JMAL_TOKEN));
        FileDocument fileDocument = getFileInfo(fileIds, username);
        try {
            nginx(request, response, isDownload, fileDocument);
        } catch (IOException e) {
            throw new CommonException(-1,e.getMessage());
        }
    }

    private void nginx(HttpServletRequest request, HttpServletResponse response, boolean isDownload, FileDocument fileDocument) throws IOException {
        if (fileDocument != null) {
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
            if (fileDocument.getIsFolder()) {
                response.setHeader("Content-Disposition", "attachment; filename=rwlock.zip");
                response.setHeader("X-Archive-Files", "zip");
                response.setHeader("X-Archive-Charset", "utf-8");
            } else {
                response.setHeader("X-Accel-Redirect", path);
            }
            if (isDownload) {
                response.setHeader("Content-Disposition", "attachment;filename=\"" + filename + "\"");
                OutputStream out = response.getOutputStream();
                if (fileDocument.getContent() != null) {
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
    public ResponseResult<Object> rename(String newFileName, String username, String id) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            String currentDirectory = getUserDirectory(fileDocument.getPath());
            String filePath = rootPath + File.separator + username + currentDirectory;
            File file = new File(filePath + fileDocument.getName());
            if (fileDocument.getIsFolder()) {
                Query query = new Query();
                String searchPath = currentDirectory + fileDocument.getName();
                String newPath = currentDirectory + newFileName;
                query.addCriteria(Criteria.where("path").regex("^" + searchPath));
                List<FileDocument> documentList = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
                // 修改该文件夹下的所有文件的path
                documentList.parallelStream().forEach(rep -> {
                    String path = rep.getPath();
                    String newFilePath = replaceStart(path, searchPath, newPath);
                    Update update = new Update();
                    update.set("path", newFilePath);
                    Query query1 = new Query();
                    query1.addCriteria(Criteria.where("_id").is(rep.getId()));
                    mongoTemplate.upsert(query1, update, COLLECTION_NAME);
                });
            }
            if (renameFile(newFileName, id, filePath, file)) {
                return ResultUtil.error("重命名失败");
            }
            return ResultUtil.success(true);
        } else {
            return ResultUtil.error("数据库查询失败");
        }
    }

    /***
     * 移动文件/文件夹
     * @param upload
     * @param froms 文件/文件夹id
     * @param to 文件夹id
     * @return
     * @throws CommonException
     */
    @Override
    public ResponseResult move(UploadApiParam upload, List<String> froms, String to) {
        // 复制
        ResponseResult result = getCopyResult(upload, froms, to);
        if (result != null) {
            return result;
        }
        // 删除
        return delete(upload.getUsername(), froms);
    }

    private ResponseResult getCopyResult(UploadApiParam upload, List<String> froms, String to) {
        for (String from : froms) {
            ResponseResult result = copy(upload, from, to);
            if (result.getCode() != 0 && result.getCode() != -2) {
                return result;
            }
        }
        return null;
    }

    /***
     * 复制文件/文件夹
     * @param upload
     * @param froms 文件/文件夹id
     * @param to 文件夹id
     * @return
     * @throws CommonException
     */
    @Override
    public ResponseResult copy(UploadApiParam upload, List<String> froms, String to) {
        // 复制
        ResponseResult result = getCopyResult(upload, froms, to);
        if (result != null) {
            return result;
        }
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> getMarkDownContent(String mark) {
        if (StringUtils.isEmpty(mark)) {
            Query query = new Query();
            query.addCriteria(Criteria.where("contentType").is(CONTENT_TYPE_MARK_DOWN));
            List<FileDocument> fileDocumentList = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
            return ResultUtil.success(fileDocumentList);
        } else {
            FileDocument fileDocument = mongoTemplate.findById(mark, FileDocument.class, COLLECTION_NAME);
            if (fileDocument != null) {
                String username = userService.userInfoById(fileDocument.getUserId()).getUsername();
                String currentDirectory = getUserDirectory(fileDocument.getPath());
                File file = new File(rootPath + File.separator + username + currentDirectory + fileDocument.getName());
                fileDocument.setContentText(FileUtil.readString(file, StandardCharsets.UTF_8));
            }
            return ResultUtil.success(fileDocument);
        }
    }

    /***
     * 新建文档
     * @param upload
     * @return
     */
    @Override
    public ResponseResult<Object> newMarkdown(UploadApiParam upload) {
        upload.setIsFolder(false);
        String filename = upload.getFilename();
        String md5 = CalcMD5.getMd5(upload.getContentText());
        //用户磁盘目录
        String currentDirectory = getUserDirectory(upload.getCurrentDirectory());
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        File file = new File(rootPath + File.separator + upload.getUsername() + currentDirectory + filename);
        FileUtil.writeString(upload.getContentText(), file, StandardCharsets.UTF_8);
        // 保存文件信息
        upload.setSuffix(FileUtil.extName(filename));
        FileDocument fileDocument = new FileDocument();
        fileDocument.setPath(currentDirectory);
        fileDocument.setSize(upload.getContentText().length());
        fileDocument.setContentType(CONTENT_TYPE_MARK_DOWN);
        fileDocument.setContentText(upload.getContentText());
        fileDocument.setMd5(md5);
        fileDocument.setName(filename);
        fileDocument.setIsFolder(false);
        saveFileInfo(upload, date, fileDocument);
        mongoTemplate.save(fileDocument, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 编辑文档
     * @param upload
     * @return
     */
    @Override
    public ResponseResult<Object> editMarkdown(UploadApiParam upload) {
        FileDocument fileDocument = mongoTemplate.findById(upload.getFileId(), FileDocument.class, COLLECTION_NAME);
        String filename = upload.getFilename();
        //用户磁盘目录
        String currentDirectory = getUserDirectory(Objects.requireNonNull(fileDocument).getPath());
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        File file = new File(rootPath + File.separator + upload.getUsername() + currentDirectory + filename);
        FileUtil.del(rootPath + File.separator + upload.getUsername() + currentDirectory + fileDocument.getName());
        FileUtil.writeString(upload.getContentText(), file, StandardCharsets.UTF_8);
        Update update = new Update();
        update.set("name", upload.getFilename());
        update.set("contentText", upload.getContentText());
        Query query = new Query().addCriteria(Criteria.where("_id").is(upload.getFileId()));
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    /***
     * 上传文档里的图片
     * @param upload
     * @return
     * @throws CommonException
     */
    @Override
    public ResponseResult<Object> uploadMarkdownImage(UploadApiParam upload) throws CommonException {
        MultipartFile multipartFile = upload.getFile();
        try {
            String markName = upload.getFilename();
            upload.setTotalSize(multipartFile.getSize());
            upload.setIsFolder(false);
            String fileName = System.currentTimeMillis()+multipartFile.getOriginalFilename();
            upload.setFilename(fileName);
            upload.setRelativePath(fileName);

            String[] docPaths = new String[]{"Image","Document Image",markName};
            String docPath = "/Image/Document Image/"+markName;
            upload.setCurrentDirectory(docPath);
            //用户磁盘目录
            String userDirectoryFilePath = getUserDirectoryFilePath(upload);
            LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);

            String username = upload.getUsername();
            String userId = upload.getUserId();
            String directoryPath = upload.getRootPath() + File.separator + upload.getUsername() + getUserDirectory(docPath);
            File dir = new File(directoryPath);
            if(!dir.exists()){
                StringBuilder parentPath = new StringBuilder();
                for (int i = 0; i < docPaths.length; i++) {
                    UploadApiParam uploadApiParam = new UploadApiParam();
                    uploadApiParam.setIsFolder(true);
                    uploadApiParam.setFilename(docPaths[i]);
                    uploadApiParam.setUsername(username);
                    uploadApiParam.setUserId(userId);
                    if(i > 0){
                        uploadApiParam.setCurrentDirectory(parentPath.toString());
                    }
                    uploadFolder(uploadApiParam);
                    parentPath.append("/").append(docPaths[i]);
                }
            }
            // 没有分片,直接存
            File newFile = new File(upload.getRootPath() + File.separator + upload.getUsername() + userDirectoryFilePath);
            FileUtil.writeFromStream(multipartFile.getInputStream(), newFile);
            // 保存文件信息
            upload.setInputStream(multipartFile.getInputStream());
            upload.setContentType(multipartFile.getContentType());
            upload.setSuffix(FileUtil.extName(fileName));
            FileDocument fileDocument = saveFileInfo(upload, CalcMD5.calcMD5(newFile), date);
            return ResultUtil.success(fileDocument.getId());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ResultUtil.error("添加图片失败");
    }

    private ResponseResult<Object> copy(UploadApiParam upload, String from, String to) {
        FileDocument formFileDocument = getFileDocumentById(from);
        String fromPath = getRelativePathByFileId(formFileDocument);
        String fromFilePath = getUserDir(upload.getUsername()) + fromPath;
        FileDocument toFileDocument = getFileDocumentById(to);
        String toPath = getRelativePathByFileId(toFileDocument);
        String toFilePath = getUserDir(upload.getUsername()) + toPath;
        if (formFileDocument != null) {
            FileUtil.copy(fromFilePath, toFilePath, true);
            if (formFileDocument.getIsFolder()) {
                // 复制文件夹
                // 复制其本身
                FileDocument copyFileDocument = copyFileDocument(formFileDocument, toPath);
                if (isExistsOfToCopy(copyFileDocument, toPath)) {
                    return ResultUtil.warning("所选目录已存在该文件夹!");
                }
                mongoTemplate.save(copyFileDocument, COLLECTION_NAME);
                // 复制其下的子文件或目录
                Query query = new Query();
                query.addCriteria(Criteria.where("path").regex("^" + fromPath));
                List<FileDocument> formList = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
                formList = formList.stream().peek(fileDocument -> {
                    String oldPath = fileDocument.getPath();
                    String newPath = toPath + oldPath.substring(1);
                    copyFileDocument(fileDocument, newPath);
                }).collect(toList());
                mongoTemplate.insert(formList, COLLECTION_NAME);
            } else {
                // 复制文件
                // 复制其本身
                FileDocument copyFileDocument = copyFileDocument(formFileDocument, toPath);
                if (isExistsOfToCopy(copyFileDocument, toPath)) {
                    return ResultUtil.warning("所选目录已存在该文件!");
                }
                mongoTemplate.save(copyFileDocument, COLLECTION_NAME);
            }
            return ResultUtil.success();
        }
        return ResultUtil.error("服务器开小差了, 请稍后再试...");
    }

    /***
     * 复制更新数据
     * @param formFileDocument
     * @param toPath
     * @return
     */
    private FileDocument copyFileDocument(FileDocument formFileDocument, String toPath) {
        formFileDocument.setId(null);
        formFileDocument.setPath(toPath);
        formFileDocument.setUpdateDate(LocalDateTime.now(TimeUntils.ZONE_ID));
        return formFileDocument;
    }

    /***
     * 目标目录是否存该文件
     * @param formFileDocument
     * @param toPath
     * @return
     */
    private boolean isExistsOfToCopy(FileDocument formFileDocument, String toPath) {
        Query query = new Query();
        query.addCriteria(Criteria.where("path").is(toPath));
        query.addCriteria(Criteria.where("name").is(formFileDocument.getName()));
        return mongoTemplate.exists(query, COLLECTION_NAME);
    }

//    private Update getUpdate(FileDocument fileDocument){
//        Update update = new Update();
//        update.set("userId", fileDocument.getUserId());
//        update.set("path", fileDocument.getPath());
//        update.set("isFolder", fileDocument.getIsFolder());
//        update.set("name", fileDocument.getName());
//        update.set("size", fileDocument.getSize());
//        update.set("uploadDate", fileDocument.getUploadDate());
//        update.set("updateDate", LocalDateTime.now(TimeUntils.ZONE_ID));
//        update.set("md5", fileDocument.getMd5());
//        update.set("contentType", fileDocument.getContentType());
//        update.set("suffix", fileDocument.getSuffix());
//        return update;
//    }

    private static String replaceStart(String str, CharSequence searchStr, CharSequence replacement) {
        return replacement + str.substring(searchStr.length());
    }

    private boolean renameFile(String newFileName, String id, String filePath, File file) {
        if (file.renameTo(new File(filePath + newFileName))) {
            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(id));
            Update update = new Update();
            update.set("name", newFileName);
            mongoTemplate.upsert(query, update, COLLECTION_NAME);
        } else {
            return true;
        }
        return false;
    }

    /***
     * 保存文件(分片)
     * @param upload
     * @return
     */
    @Override
    public ResponseResult<Object> upload(UploadApiParam upload) throws IOException {
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
            // 没有分片,直接存
            File chunkFile = new File(upload.getRootPath() + File.separator + upload.getUsername() + userDirectoryFilePath);
            FileUtil.writeFromStream(file.getInputStream(), chunkFile);
            // 保存文件信息
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
            File chunkFile = new File(upload.getRootPath() + File.separator + TEMPORARY_DIRECTORY + File.separator + upload.getUsername() + File.separator + md5 + File.separator + upload.getChunkNumber());
            FileUtil.writeFromStream(file.getInputStream(), chunkFile);
            setResumeCache(upload);
            uploadResponse.setUpload(true);
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
    public ResponseResult<Object> uploadFolder(UploadApiParam upload) throws CommonException {
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        // 新建文件夹
        String userDirectoryFilePath = getUserDirectoryFilePath(upload);
        //没有分片,直接存
        File dir = new File(upload.getRootPath() + File.separator + upload.getUsername() + userDirectoryFilePath);
        if (!dir.exists()) {
            FileUtil.mkdir(dir);
//            if (!dir.mkdir()) {
//                String error = String.format("创建文件夹失败,dir:%s", dir.getAbsolutePath());
//                log.error(error);
//                return ResultUtil.error(error);
//            }
        }
        // 保存文件夹信息
        saveFolderInfo(upload, date);
        return ResultUtil.success();
    }

    /***
     * 保存文件信息
     * @param upload
     * @param md5
     * @param date
     */
    private FileDocument saveFileInfo(UploadApiParam upload, String md5, LocalDateTime date) throws IOException {
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
        saveFileInfo(upload, date, fileDocument);
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
        if (contentType.contains(CONTENT_TYPE_MARK_DOWN)) {
            // 写入markdown内容
            byte[] content = toByteArray(upload.getInputStream());
            fileDocument.setContentText(new String(content,0,content.length,StandardCharsets.UTF_8));
        }
        return mongoTemplate.save(fileDocument, COLLECTION_NAME);
    }

    /***
     * 部分文件信息
     * @param upload
     * @param date
     * @param fileDocument
     */
    private void saveFileInfo(UploadApiParam upload, LocalDateTime date, FileDocument fileDocument) {
        fileDocument.setIsFavorite(false);
        fileDocument.setUploadDate(date);
        fileDocument.setUpdateDate(date);
        fileDocument.setSuffix(upload.getSuffix());
        fileDocument.setUserId(upload.getUserId());
    }

    private static byte[] toByteArray(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int n;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
        }
        return output.toByteArray();
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
        update.set("name", folderName);
        update.set("uploadDate", date);
        update.set("updateDate", date);
        update.set("isFavorite", false);
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
    }

    private void setResumeCache(UploadApiParam upload) {
        int chunkNumber = upload.getChunkNumber();
        String md5 = upload.getIdentifier();
        CopyOnWriteArrayList<Integer> chunks = resumeCache.get(md5, key -> createResumeCache(upload));
        assert chunks != null;
        if (!chunks.contains(chunkNumber)) {
            chunks.add(chunkNumber);
            resumeCache.put(md5, chunks);
        }
    }

    /***
     * 获取已经保存的分片索引
     */
    private CopyOnWriteArrayList<Integer> getSavedChunk(UploadApiParam upload) {
        String md5 = upload.getIdentifier();
        return resumeCache.get(md5, key -> createResumeCache(upload));
    }

    /***
     * 检测是否需要合并
     */
    private boolean checkIsNeedMerge(UploadApiParam upload) {
        int totalChunks = upload.getTotalChunks();
        CopyOnWriteArrayList<Integer> chunkList = getSavedChunk(upload);
        System.out.println("totalChunks:" + totalChunks + ",chunkList:" + chunkList.size());
        return totalChunks == chunkList.size();
    }

    /***
     * 读取分片文件是否存在
     * @return
     */
    private CopyOnWriteArrayList<Integer> createResumeCache(UploadApiParam upload) {
        CopyOnWriteArrayList<Integer> resumeList = new CopyOnWriteArrayList<>();
        String md5 = upload.getIdentifier();
        // 读取tmp分片目录所有文件
        File f = new File(upload.getRootPath() + File.separator + TEMPORARY_DIRECTORY + File.separator + upload.getUsername() + File.separator + md5);
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
    public ResponseResult<Object> checkChunkUploaded(UploadApiParam upload) throws IOException {
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
            if (totalChunks == chunks.size()) {
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
    @SuppressWarnings("resource")
    @Override
    public ResponseResult<Object> merge(UploadApiParam upload) throws IOException {
        UploadResponse uploadResponse = new UploadResponse();
        String md5 = upload.getIdentifier();
        String filename = upload.getFilename();
        // 用户磁盘目录
        String userDirectoryFilePath = getUserDirectoryFilePath(upload);

        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);

        // 读取tmp目录所有文件
        File f = new File(upload.getRootPath() + File.separator + TEMPORARY_DIRECTORY + File.separator + File.separator + upload.getUsername() + File.separator + md5);
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
                throw new CommonException(-1, String.format("创建文件夹失败,dir:%s", parentFile.getAbsolutePath()));
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
            currentDirectory = DIR_SEPARATOR;
        }
        if (upload.getIsFolder()) {
            if (upload.getFolderPath() != null) {
                currentDirectory += DIR_SEPARATOR + upload.getFolderPath();
            } else {
                currentDirectory += DIR_SEPARATOR + upload.getFilename();
            }
        } else {
            currentDirectory += DIR_SEPARATOR + upload.getRelativePath();
        }
        currentDirectory = currentDirectory.replaceAll(DIR_SEPARATOR, File.separator);
        return currentDirectory;
    }

    private String getUserDirectory(String currentDirectory) {
        if (StringUtils.isEmpty(currentDirectory)) {
            currentDirectory = DIR_SEPARATOR;
        } else {
            if (!currentDirectory.endsWith(DIR_SEPARATOR)) {
                currentDirectory += DIR_SEPARATOR;
            }
        }
        currentDirectory = currentDirectory.replaceAll(DIR_SEPARATOR, File.separator);
        return currentDirectory;
    }

    /***
     * 收藏文件或文件夹
     * @param fileId
     * @return
     * @throws CommonException
     */
    @Override
    public ResponseResult<Object> favorite(String fileId) throws CommonException {
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
    public ResponseResult<Object> unFavorite(String fileId) {
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
    public ResponseResult<Object> delete(String username, List<String> fileIds) {
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
                query1.addCriteria(Criteria.where("path").regex("^" + fileDocument.getPath() + fileDocument.getName()));
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

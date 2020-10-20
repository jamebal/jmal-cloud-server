package com.jmal.clouddisk.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.github.benmanes.caffeine.cache.Cache;
import com.google.common.collect.Maps;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.*;
import com.jmal.clouddisk.websocket.SocketManager;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.tasks.UnsupportedFormatException;
import org.apache.commons.compress.utils.Lists;
import org.bson.BsonNull;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.socket.WebSocketSession;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.*;
import static java.util.stream.Collectors.toList;

/**
 * @author jmal
 * @Description 文件管理
 * @Author jmal
 * @Date 2020-01-14 13:05
 */
@Service
@Slf4j
public class FileServiceImpl implements IFileService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    IUserService userService;

    @Autowired
    FilePropertie filePropertie;

    @Autowired
    IShareService shareService;

    @Autowired
    private SimpMessagingTemplate template;

    private static final String COLLECTION_NAME = "fileDocument";
    private static final String CONTENT_TYPE_IMAGE = "image";
    private static final String CONTENT_TYPE_MARK_DOWN = "text/markdown";

    /***
     * 前端文件夹树的第一级的文件Id
     */
    private static final String FIRST_FILE_TREE_ID = "0";

    private static final AES aes = SecureUtil.aes();

    /***
     * 断点恢复上传缓存(以上传的缓存)
     */
    private final Cache<String, CopyOnWriteArrayList<Integer>> resumeCache = CaffeineUtil.getResumeCache();
    /***
     * 上传大文件是需要分片上传，再合并
     * 以写入(合并)的分片缓存
     */
    private final Cache<String, CopyOnWriteArrayList<Integer>> writtenCache = CaffeineUtil.getWrittenCache();
    /***
     * 未写入(合并)的分片缓存
     */
    private final Cache<String, CopyOnWriteArrayList<Integer>> unWrittenCache = CaffeineUtil.getUnWrittenCacheCache();
    /***
     * 合并文件是的写入锁缓存
     */
    private final Cache<String, Lock> chunkWriteLockCache = CaffeineUtil.getChunkWriteLockCache();

    @Override
    public ResponseResult<Object> listFiles(UploadApiParamDTO upload) throws CommonException {
        ResponseResult<Object> result = ResultUtil.genResult();
        String currentDirectory = getUserDirectory(upload.getCurrentDirectory());
        Criteria criteria;
        String queryFileType = upload.getQueryFileType();
        if (!StringUtils.isEmpty(queryFileType)) {
            switch (upload.getQueryFileType()) {
                case "audio":
                    criteria = Criteria.where("contentType").regex("^audio");
                    break;
                case "video":
                    criteria = Criteria.where("contentType").regex("^video");
                    break;
                case "image":
                    criteria = Criteria.where("contentType").regex("^image");
                    break;
                case "text":
                    criteria = Criteria.where("suffix").in(Arrays.asList(filePropertie.getSimText()));
                    break;
                case "document":
                    criteria = Criteria.where("suffix").in(Arrays.asList(filePropertie.getDoument()));
                    break;
                default:
                    criteria = Criteria.where("path").is(currentDirectory);
                    break;
            }
        } else {
            criteria = Criteria.where("path").is(currentDirectory);
            Boolean isFolder = upload.getIsFolder();
            if (isFolder != null) {
                criteria = Criteria.where("isFolder").is(isFolder);
            }
            Boolean isFavorite = upload.getIsFavorite();
            if (isFavorite != null) {
                criteria = Criteria.where("isFavorite").is(isFavorite);
            }
        }
        List<FileDocument> list = getFileDocuments(upload, criteria);
        result.setData(list);
        result.setCount(getFileDocumentsCount(upload, criteria));
        return result;
    }

    @Override
    public long takeUpSpace(String userId) throws CommonException {

        List<Bson> list = Arrays.asList(
                match(eq("userId", userId)),
                group(new BsonNull(), sum("totalSize", "$size")));
        AggregateIterable aggregateIterable = mongoTemplate.getCollection(COLLECTION_NAME).aggregate(list);
        Document doc = (Document) aggregateIterable.first();
        if (doc != null) {
            return doc.getLong("totalSize");
        }
        return 0;
    }

    /***
     * 通过查询条件获取文件数
     * @param upload
     * @param criteriaList
     * @return
     */
    private long getFileDocumentsCount(UploadApiParamDTO upload, Criteria... criteriaList) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(upload.getUserId()));
        for (Criteria criteria : criteriaList) {
            query.addCriteria(criteria);
        }
        return mongoTemplate.count(query, COLLECTION_NAME);
    }

    private List<FileDocument> getFileDocuments(UploadApiParamDTO upload, Criteria... criteriaList) {
        Query query = getQuery(upload, criteriaList);
        String order = ShareServiceImpl.listByPage(upload, query);
        if (!StringUtils.isEmpty(order)) {
            String sortableProp = upload.getSortableProp();
            Sort.Direction direction = Sort.Direction.ASC;
            if ("descending".equals(order)) {
                direction = Sort.Direction.DESC;
            }
            query.with(new Sort(direction, sortableProp));
        } else {
            query.with(new Sort(Sort.Direction.DESC, "isFolder"));
        }
        query.fields().exclude("content").exclude("music.coverBase64");
        List<FileDocument> list = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        long now = System.currentTimeMillis();

        list = list.parallelStream().peek(fileDocument -> {
            LocalDateTime updateDate = fileDocument.getUpdateDate();
            long update = TimeUntils.getMilli(updateDate);
            fileDocument.setAgoTime(now - update);
            if (fileDocument.getIsFolder()) {
                String path = fileDocument.getPath() + fileDocument.getName() + File.separator;
                long size = getFolderSize(fileDocument.getUserId(), path);
                fileDocument.setSize(size);
            }
        }).collect(toList());
        // 按文件名排序
        if (StringUtils.isEmpty(order)) {
            list.sort(this::compareByFileName);
        }
        if (!StringUtils.isEmpty(order) && "name".equals(upload.getSortableProp())) {
            list.sort(this::compareByFileName);
            if ("descending".equals(order)) {
                list.sort(this::desc);
            }
        }
        return list;
    }

    private int desc(FileDocument f1, FileDocument f2) {
        return -1;
    }

    private List<FileDocument> getDirDocuments(UploadApiParamDTO upload, Criteria... criteriaList) {
        Query query = getQuery(upload, criteriaList);
        query.addCriteria(Criteria.where("isFolder").is(true));
        List<FileDocument> list = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        // 按文件名排序
        list.sort(this::compareByFileName);
        return list;
    }

    private Query getQuery(UploadApiParamDTO upload, Criteria[] criteriaList) {
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
    public ResponseResult<Object> searchFile(UploadApiParamDTO upload, String keyword) throws CommonException {
        ResponseResult<Object> result = ResultUtil.genResult();
        Criteria criteria1 = Criteria.where("name").regex(keyword);
        return getCountResponseResult(upload, result, criteria1);
    }

    private ResponseResult<Object> getCountResponseResult(UploadApiParamDTO upload, ResponseResult<Object> result, Criteria... criteriaList) {
        List<FileDocument> list = getFileDocuments(upload, criteriaList);
        result.setData(list);
        result.setCount(getFileDocumentsCount(upload, criteriaList));
        return result;
    }

    @Override
    public ResponseResult<Object> searchFileAndOpenDir(UploadApiParamDTO upload, String id) throws CommonException {
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

    /***
     * 通过文件Id获取文件的相对路径
     * @param fileDocument
     * @return
     */
    private String getRelativePathByFileId(FileDocument fileDocument) {
        if (fileDocument == null) {
            return getUserDirectory(null);
        }
        if (fileDocument.getIsFolder()) {
            return getUserDirectory(fileDocument.getPath() + fileDocument.getName());
        }
        String currentDirectory = fileDocument.getPath() + fileDocument.getName();
        return currentDirectory.replaceAll(filePropertie.getSeparator(), File.separator);
    }

    /***
     * 获取用户的绝对目录
     * @param userName
     * @return
     */
    private String getUserDir(String userName) {
        return filePropertie.getRootDir() + File.separator + userName;
    }


    @Override
    public ResponseResult<Object> queryFileTree(UploadApiParamDTO upload, String fileId) {
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

    @Override
    public Optional<Object> getById(String id, String username) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            String currentDirectory = getUserDirectory(fileDocument.getPath());
            Path filepath = Paths.get(filePropertie.getRootDir(), username, currentDirectory, fileDocument.getName());
            if (Files.exists(filepath)) {
                File file = filepath.toFile();
                if (file.length() > 1024 * 1024 * 5) {
                    fileDocument.setContentText(MyFileUtils.readLines(file, 1000));
                } else {
                    fileDocument.setContentText(FileUtil.readString(file, StandardCharsets.UTF_8));
                }
            }
            return Optional.of(fileDocument);
        }
        return Optional.empty();
    }

    @Override
    public ResponseResult<Object> previewTextByPath(String filePath, String username) throws CommonException {
        Path path = Paths.get(filePropertie.getRootDir(), username, filePath);
        File file = path.toFile();
        if (!file.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        FileDocument fileDocument = new FileDocument();
        if (file.length() > 1024 * 1024 * 5) {
            fileDocument.setContentText(MyFileUtils.readLines(file, 1000));
        } else {
            fileDocument.setContentText(FileUtil.readString(file, StandardCharsets.UTF_8));
        }
        Path path1 = path.subpath(0, path.getNameCount() - 1);
        int rootCount = Paths.get(filePropertie.getRootDir(), username).getNameCount();
        int path1Count = path1.getNameCount();
        String resPath = "/";
        if (rootCount < path1Count) {
            resPath = path1.subpath(rootCount, path1Count).toString();
        }
        fileDocument.setPath(resPath);
        fileDocument.setName(file.getName());
        fileDocument.setIsFolder(file.isDirectory());
        return ResultUtil.success(fileDocument);
    }

    @Override
    public Optional<FileDocument> thumbnail(String id, String username) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            if (fileDocument.getContent() == null) {
                String currentDirectory = getUserDirectory(fileDocument.getPath());
                File file = new File(filePropertie.getRootDir() + File.separator + username + currentDirectory + fileDocument.getName());
                if (file.exists()) {
                    fileDocument.setContent(FileUtil.readBytes(file));
                }
            }
            return Optional.of(fileDocument);
        }
        return Optional.empty();
    }

    @Override
    public Optional<FileDocument> coverOfMp3(String id, String userName) throws CommonException {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument == null) {
            return Optional.empty();
        }
        fileDocument.setContentType("image/png");
        fileDocument.setName("cover");
        String base64 = Optional.of(fileDocument).map(FileDocument::getMusic).map(Music::getCoverBase64).orElse("");
        fileDocument.setContent(Base64.decode(base64));
        return Optional.of(fileDocument);
    }

    @Override
    public void publicPackageDownload(HttpServletRequest request, HttpServletResponse response, List<String> fileIdList) {
        packageDownload(request, response, fileIdList, null);
    }

    @Override
    public void packageDownload(HttpServletRequest request, HttpServletResponse response, List<String> fileIdList) {
        String username = userService.getUserName(request.getParameter(AuthInterceptor.JMAL_TOKEN));
        packageDownload(request, response, fileIdList, username);
    }

    private void packageDownload(HttpServletRequest request, HttpServletResponse response, List<String> fileIdList, String username) {
        FileDocument fileDocument = getFileInfoBeforeDownload(fileIdList, username);
        if(StringUtils.isEmpty(username)){
            username = fileDocument.getUsername();
        }
        //响应头的设置
        response.reset();
        response.setCharacterEncoding("utf-8");
        response.setContentType("multipart/form-data");
        //设置压缩包的名字
        setDownloadName(request, response, fileDocument.getName() + ".zip");

        Path srcDir = Paths.get(filePropertie.getRootDir(), username, getUserDirectory(fileDocument.getPath()));
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIdList));
        List<FileDocument> fileDocuments = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        // 选中的文件
        List<String> selectNameList = new ArrayList<>();
        selectNameList = fileDocuments.stream().map(doc -> {
            return doc.getName();
        }).collect(toList());
        List<String> finalSelectNameList = selectNameList;
        File[] excludeFiles = srcDir.toFile().listFiles(file -> !finalSelectNameList.contains(file.getName()));
        List<Path> excludeFilePathList = new ArrayList<>();
        for (File excludeFile : excludeFiles) {
            excludeFilePathList.add(excludeFile.toPath());
        }
        // 压缩传输
        try {
            CompressUtils.zip(srcDir, excludeFilePathList, response.getOutputStream());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    /***
     * 对下载的文件名转码 解决不同浏览器压缩包名字含有中文时乱码的问题
     * @param request
     * @param downloadName
     * @return
     * @throws UnsupportedEncodingException
     */
    private String setDownloadName(HttpServletRequest request, HttpServletResponse response, String downloadName){
        try {
            //获取浏览器名（IE/Chrome/firefox）目前主流的四大浏览器内核Trident(IE)、Gecko(Firefox内核)、WebKit(Safari内核,Chrome内核原型,开源)以及Presto(Opera前内核) (已废弃)
            String gecko = "Gecko", webKit = "WebKit";
            String userAgent = request.getHeader("User-Agent");
            if (userAgent.contains(gecko) || userAgent.contains(webKit)) {
                downloadName = new String(downloadName.getBytes(StandardCharsets.UTF_8), "ISO8859-1");
            } else {
                downloadName = URLEncoder.encode(downloadName, "UTF-8");
            }
            response.setHeader("Content-Disposition", "attachment;fileName=\"" + downloadName + "\"");
        } catch (UnsupportedEncodingException e) {
            log.error(e.getMessage(), e);
        }
        return downloadName;
    }

    /***
     * 在nginx之前获取文件信息
     * @param fileIds
     * @param username
     * @return
     */
    private FileDocument getFileInfoBeforeDownload(List<String> fileIds, String username) throws CommonException {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds.get(0)));
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
        if(StringUtils.isEmpty(username)){
            fileDocument.setUsername(userService.getUserNameById(fileDocument.getUserId()));
        }
        int size = fileIds.size();
        if (size > 0) {
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
            } else {
                fileDocument.setName(filename);
            }
            return fileDocument;
        }
        return fileDocument;
    }

    @Override
    public ResponseResult<Object> rename(String newFileName, String username, String id) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            String currentDirectory = getUserDirectory(fileDocument.getPath());
            String filePath = filePropertie.getRootDir() + File.separator + username + currentDirectory;
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

    @Override
    public ResponseResult move(UploadApiParamDTO upload, List<String> froms, String to) {
        // 复制
        ResponseResult result = getCopyResult(upload, froms, to);
        if (result != null) {
            return result;
        }
        // 删除
        return delete(upload.getUsername(), froms);
    }

    private ResponseResult getCopyResult(UploadApiParamDTO upload, List<String> froms, String to) {
        for (String from : froms) {
            ResponseResult result = copy(upload, from, to);
            if (result.getCode() != 0 && result.getCode() != -2) {
                return result;
            }
        }
        return null;
    }

    @Override
    public ResponseResult copy(UploadApiParamDTO upload, List<String> froms, String to) {
        // 复制
        ResponseResult result = getCopyResult(upload, froms, to);
        if (result != null) {
            return result;
        }
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> newMarkdown(UploadApiParamDTO upload) {
        upload.setIsFolder(false);
        String filename = upload.getFilename();
        String md5 = CalcMd5.getMd5(upload.getContentText());
        //用户磁盘目录
        String currentDirectory = getUserDirectory(upload.getCurrentDirectory());
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        File file = new File(filePropertie.getRootDir() + File.separator + upload.getUsername() + currentDirectory + filename);
        FileUtil.writeString(upload.getContentText(), file, StandardCharsets.UTF_8);
        // 保存文件信息
        upload.setSuffix(FileUtil.extName(filename));
        FileDocument fileDocument = new FileDocument();
        fileDocument.setPath(currentDirectory);
        fileDocument.setSize(upload.getContentText().length());
        fileDocument.setContentType(CONTENT_TYPE_MARK_DOWN);
        fileDocument.setMd5(md5);
        fileDocument.setName(filename);
        fileDocument.setIsFolder(false);
        createFile(upload.getUsername(), file);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> getMarkDownContent(String mark) throws CommonException {
        if (StringUtils.isEmpty(mark)) {
            Query query = new Query();
            query.addCriteria(Criteria.where("isFavorite").is(true));
            query.addCriteria(Criteria.where("suffix").is("md"));
            query.with(new Sort(Sort.Direction.DESC, "uploadDate"));
            List<FileDocument> fileDocumentList = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
            fileDocumentList = fileDocumentList.parallelStream().peek(fileDocument -> {
                Consumer user = userService.userInfoById(fileDocument.getUserId());
                String avatar = user.getAvatar();
                fileDocument.setUsername(user.getUsername());
                String filename = fileDocument.getName();
                fileDocument.setName(filename.substring(0, filename.length() - fileDocument.getSuffix().length() - 1));
                LocalDateTime date = fileDocument.getUploadDate();
                String sb = date.getYear() + "年" +
                        date.getMonthValue() + "月" +
                        date.getDayOfMonth() + "日";
                fileDocument.setUploadTime(sb);
                fileDocument.setAvatar(avatar);
            }).collect(toList());
            return ResultUtil.success(fileDocumentList);
        } else {
            FileDocument fileDocument = mongoTemplate.findById(mark, FileDocument.class, COLLECTION_NAME);
            if (fileDocument != null) {
                String username = userService.userInfoById(fileDocument.getUserId()).getUsername();
                String currentDirectory = getUserDirectory(fileDocument.getPath());
                File file = new File(filePropertie.getRootDir() + File.separator + username + currentDirectory + fileDocument.getName());
                String content = FileUtil.readString(file, StandardCharsets.UTF_8);
                if ("md".equals(fileDocument.getSuffix()) && !StringUtils.isEmpty(content)) {
                    content = replaceAll(content, fileDocument.getPath(), fileDocument.getUserId());
                    fileDocument.setContentText(content);
                } else {
                    fileDocument.setContentText(content);
                }
            }
            return ResultUtil.success(fileDocument);
        }
    }

    @Override
    public ResponseResult<Object> editMarkdown(UploadApiParamDTO upload) {
        FileDocument fileDocument = mongoTemplate.findById(upload.getFileId(), FileDocument.class, COLLECTION_NAME);
        String filename = upload.getFilename();
        //用户磁盘目录
        String currentDirectory = getUserDirectory(Objects.requireNonNull(fileDocument).getPath());
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        File file = new File(filePropertie.getRootDir() + File.separator + upload.getUsername() + currentDirectory + filename);
        FileUtil.del(filePropertie.getRootDir() + File.separator + upload.getUsername() + currentDirectory + fileDocument.getName());
        FileUtil.writeString(upload.getContentText(), file, StandardCharsets.UTF_8);
        Update update = new Update();
        update.set("size", FileUtil.size(file));
        update.set("name", upload.getFilename());
        update.set("updateDate", date);
        Query query = new Query().addCriteria(Criteria.where("_id").is(upload.getFileId()));
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> editMarkdownByPath(UploadApiParamDTO upload) {
        File file = new File(Paths.get(filePropertie.getRootDir(), upload.getUsername(), upload.getRelativePath()).toString());
        if (!file.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        FileUtil.writeString(upload.getContentText(), file, StandardCharsets.UTF_8);
        updateFile(upload.getUsername(), file);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> uploadMarkdownImage(UploadApiParamDTO upload) throws CommonException {
        MultipartFile multipartFile = upload.getFile();
        try {
            String markName = upload.getFilename();
            upload.setTotalSize(multipartFile.getSize());
            upload.setIsFolder(false);
            String fileName = System.currentTimeMillis() + multipartFile.getOriginalFilename();
            upload.setFilename(fileName);
            upload.setRelativePath(fileName);

            String[] docPaths = new String[]{"Image", "Document Image", markName};
            String docPath = "/Image/Document Image/" + markName;
            upload.setCurrentDirectory(docPath);
            //用户磁盘目录
            String userDirectoryFilePath = getUserDirectoryFilePath(upload);
            LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);

            String username = upload.getUsername();
            String userId = upload.getUserId();
            String directoryPath = filePropertie.getRootDir() + File.separator + upload.getUsername() + getUserDirectory(docPath);
            File dir = new File(directoryPath);
            if (!dir.exists()) {
                StringBuilder parentPath = new StringBuilder();
                for (int i = 0; i < docPaths.length; i++) {
                    UploadApiParamDTO uploadApiParamDTO = new UploadApiParamDTO();
                    uploadApiParamDTO.setIsFolder(true);
                    uploadApiParamDTO.setFilename(docPaths[i]);
                    uploadApiParamDTO.setUsername(username);
                    uploadApiParamDTO.setUserId(userId);
                    if (i > 0) {
                        uploadApiParamDTO.setCurrentDirectory(parentPath.toString());
                    }
                    uploadFolder(uploadApiParamDTO);
                    parentPath.append("/").append(docPaths[i]);
                }
            }
            File newFile = new File(filePropertie.getRootDir() + File.separator + upload.getUsername() + userDirectoryFilePath);
            // 保存文件信息
            upload.setInputStream(multipartFile.getInputStream());
            upload.setContentType(multipartFile.getContentType());
            upload.setSuffix(FileUtil.extName(fileName));
            // 没有分片,直接存
            FileUtil.writeFromStream(multipartFile.getInputStream(), newFile);
            String fileId = null;
            if (!filePropertie.getMonitor() || filePropertie.getTimeInterval() >= 3L) {
                fileId = createFile(username, newFile);
            }
            return ResultUtil.success(fileId);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ResultUtil.error("添加图片失败");
    }

    @Override
    public String uploadConsumerImage(UploadApiParamDTO upload) throws CommonException {
        MultipartFile multipartFile = upload.getFile();
        try {
            upload.setTotalSize(multipartFile.getSize());
            upload.setIsFolder(false);
            String fileName = upload.getFilename();
            upload.setFilename(fileName);
            upload.setRelativePath(fileName);

            String[] docPaths = new String[]{"Image"};
            String docPath = "/Image";
            upload.setCurrentDirectory(docPath);
            //用户磁盘目录
            String userDirectoryFilePath = getUserDirectoryFilePath(upload);
            LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);

            String username = upload.getUsername();
            String userId = upload.getUserId();
            String directoryPath = filePropertie.getRootDir() + File.separator + upload.getUsername() + getUserDirectory(docPath);
            File dir = new File(directoryPath);
            if (!dir.exists()) {
                for (String path : docPaths) {
                    UploadApiParamDTO uploadApiParamDTO = new UploadApiParamDTO();
                    uploadApiParamDTO.setIsFolder(true);
                    uploadApiParamDTO.setFilename(path);
                    uploadApiParamDTO.setUsername(username);
                    uploadApiParamDTO.setUserId(userId);
                    uploadFolder(uploadApiParamDTO);
                }
            }
            // 没有分片,直接存
            File newFile = new File(filePropertie.getRootDir() + File.separator + upload.getUsername() + userDirectoryFilePath);
            // 保存文件信息
            upload.setInputStream(multipartFile.getInputStream());
            upload.setContentType(multipartFile.getContentType());
            upload.setSuffix(FileUtil.extName(fileName));
            FileDocument fileDocument = saveFileInfo(upload, upload.getTotalSize() + fileName, date);
            FileUtil.writeFromStream(multipartFile.getInputStream(), newFile);
            return fileDocument.getId();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "";
    }

    @Override
    public FileDocument getById(String fileId) {
        return getFileDocumentById(fileId);
    }

    @Override
    public String createFile(String username, File file) {
        String userId = userService.getUserIdByUserName(username);
        if (StringUtils.isEmpty(userId)) {
            return null;
        }
        String fileAbsolutePath = file.getAbsolutePath();
        String fileName = file.getName();
        String relativePath = fileAbsolutePath.substring(filePropertie.getRootDir().length() + username.length() + 1, fileAbsolutePath.length() - fileName.length());
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("path").is(relativePath));
        query.addCriteria(Criteria.where("name").is(fileName));

        String suffix = FileUtil.extName(fileName);
        String contentType = FileContentTypeUtils.getContentType(suffix);

        // 文件是否存在
        boolean fileExists = mongoTemplate.exists(query, COLLECTION_NAME);
        if (fileExists) {
            if (contentType.contains("audio")) {
                Update update = new Update();
                Music music = AudioFileUtils.readAudio(file);
                update.set("music", music);
                mongoTemplate.upsert(query, update, COLLECTION_NAME);
            }
            return null;
        }
        LocalDateTime nowDateTime = LocalDateTime.now(TimeUntils.ZONE_ID);
        Update update = new Update();
        update.set("userId", userId);
        update.set("name", fileName);
        update.set("path", relativePath);
        update.set("isFolder", file.isDirectory());
        update.set("uploadDate", nowDateTime);
        update.set("updateDate", nowDateTime);
        if (file.isFile()) {
            long size = file.length();
            update.set("size", size);
            update.set("md5", size + relativePath + fileName);
            if (contentType.contains("audio")) {
                Music music = AudioFileUtils.readAudio(file);
                update.set("music", music);
            }
            if (contentType.startsWith(CONTENT_TYPE_IMAGE)) {
                // 生成缩略图
                Thumbnails.Builder<? extends File> thumbnail = Thumbnails.of(file);
                thumbnail.size(256, 256);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                try {
                    thumbnail.toOutputStream(out);
                    update.set("content", out.toByteArray());
                } catch (UnsupportedFormatException e) {
                    log.warn(e.getMessage());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (contentType.contains(CONTENT_TYPE_MARK_DOWN) || "md".equals(suffix)) {
                // 写入markdown内容
                String markDownContent = FileUtil.readString(file, StandardCharsets.UTF_8);
                update.set("contentText", markDownContent);
            }
            update.set("contentType", contentType);
            update.set("suffix", suffix);
            update.set("isFavorite", false);
        }
        UpdateResult updateResult = mongoTemplate.upsert(query, update, COLLECTION_NAME);
        pushMessage(username, update.getUpdateObject(), "createFile");
        if (null != updateResult.getUpsertedId()) {
            return updateResult.getUpsertedId().asObjectId().getValue().toHexString();
        }
        return null;
    }

    @Override
    public String updateFile(String username, File file) {
        String fileAbsolutePath = file.getAbsolutePath();
        String fileName = file.getName();
        String relativePath = fileAbsolutePath.substring(filePropertie.getRootDir().length() + username.length() + 1, fileAbsolutePath.length() - fileName.length());
        String userId = userService.getUserIdByUserName(username);
        if (StringUtils.isEmpty(userId)) {
            return null;
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("path").is(relativePath));
        query.addCriteria(Criteria.where("name").is(fileName));

        String suffix = FileUtil.extName(fileName);
        String contentType = FileContentTypeUtils.getContentType(suffix);

        // 文件是否存在
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            Update update = new Update();
            update.set("size", file.length());
            update.set("suffix", suffix);
            update.set("contentType", contentType);
            LocalDateTime updateDate = LocalDateTime.now(TimeUntils.ZONE_ID);
            update.set("updateDate", updateDate);
            UpdateResult updateResult = mongoTemplate.upsert(query, update, COLLECTION_NAME);
            fileDocument.setSize(file.length());
            fileDocument.setUpdateDate(updateDate);
            pushMessage(username, fileDocument, "updateFile");
            if (null != updateResult.getUpsertedId()) {
                return updateResult.getUpsertedId().asObjectId().getValue().toHexString();
            }
        }
        return null;
    }

    /***
     * 给用户推送消息
     * @param username
     * @param message
     * @param url
     */
    private void pushMessage(String username, Object message, String url) {
        WebSocketSession webSocketSession = SocketManager.get(username);
        if (webSocketSession != null) {
            Map<String, Object> headers = Maps.newHashMap();
            headers.put("url", url);
            template.convertAndSendToUser(username, "/queue/update", message, headers);
        }
    }

    @Override
    public void deleteFile(String username, File file) {
        String fileAbsolutePath = file.getAbsolutePath();
        String fileName = file.getName();
        String relativePath = fileAbsolutePath.substring(filePropertie.getRootDir().length() + username.length() + 1, fileAbsolutePath.length() - fileName.length());
        String userId = userService.getUserIdByUserName(username);
        if (StringUtils.isEmpty(userId)) {
            return;
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("path").is(relativePath));
        query.addCriteria(Criteria.where("name").is(fileName));
        // 文件是否存在
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            mongoTemplate.remove(query, COLLECTION_NAME);
            pushMessage(username, fileDocument, "deleteFile");
        }
    }

    @Override
    public ResponseResult<Object> unzip(String fileId, String destFileId) throws CommonException {
        try {
            FileDocument fileDocument = getById(fileId);
            if (fileDocument == null) {
                throw new CommonException(ExceptionType.FILE_NOT_FIND);
            }
            String username = userService.getUserNameById(fileDocument.getUserId());
            if (StringUtils.isEmpty(username)) {
                throw new CommonException(ExceptionType.USER_NOT_FIND);
            }
            String filePath = getFilePathByFileId(username, fileDocument);

            String destDir;
            boolean isWrite = false;
            if (StringUtils.isEmpty(destFileId)) {
                // 没有目标目录, 则预览解压到临时目录
                destDir = Paths.get(filePropertie.getRootDir(), filePropertie.getChunkFileDir(), username, fileDocument.getName()).toString();
            } else {
                if (fileId.equals(destFileId)) {
                    // 解压到当前文件夹
                    destDir = filePath.substring(0, filePath.length() - FileUtil.extName(new File(filePath)).length() - 1);
                } else {
                    // 其他目录
                    FileDocument dest = getById(destFileId);
                    if (dest != null) {
                        destDir = getFilePathByFileId(username, dest);
                    } else {
                        destDir = Paths.get(filePropertie.getRootDir(), username).toString();
                    }
                }
                isWrite = true;
            }

            CompressUtils.decompress(filePath, destDir, isWrite);
            return ResultUtil.success(listfile(username, destDir, !isWrite));
        } catch (Exception e) {
            return ResultUtil.error("解压失败!");
        }
    }

    @Override
    public ResponseResult<Object> listFiles(String path, String username, boolean tempDir) {
        String dirPath;
        if (tempDir) {
            dirPath = Paths.get(filePropertie.getRootDir(), filePropertie.getChunkFileDir(), username, path).toString();
        } else {
            dirPath = Paths.get(filePropertie.getRootDir(), username, path).toString();
        }
        return ResultUtil.success(listfile(username, dirPath, tempDir));
    }

    @Override
    public ResponseResult upperLevelList(String path, String username) {
        String upperLevel = Paths.get(filePropertie.getRootDir(), username, path).getParent().toString();
        if (Paths.get(filePropertie.getRootDir()).toString().equals(upperLevel)) {
            upperLevel = Paths.get(filePropertie.getRootDir(), username).toString();
        }
        return ResultUtil.success(listfile(username, upperLevel, false));
    }

    @Override
    public ResponseResult delFile(String path, String username) throws CommonException {
        Path p = Paths.get(filePropertie.getRootDir(), username, path);
        FileUtil.del(p);
        deleteFile(username, p.toFile());
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> renameByPath(String newFileName, String username, String path) {
        Path path1 = Paths.get(filePropertie.getRootDir(), username, path);
        if (!Files.exists(path1)) {
            return ResultUtil.error("修改失败,path参数有误！");
        }
        String userId = userService.getUserIdByUserName(username);
        if (StringUtils.isEmpty(userId)) {
            return ResultUtil.error("修改失败,userId参数有误！");
        }
        File file = path1.toFile();
        String fileAbsolutePath = file.getAbsolutePath();
        String fileName = file.getName();
        String relativePath = fileAbsolutePath.substring(filePropertie.getRootDir().length() + username.length() + 1, fileAbsolutePath.length() - fileName.length());
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("path").is(relativePath));
        query.addCriteria(Criteria.where("name").is(fileName));
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
        if (fileDocument == null) {
            return ResultUtil.error("修改失败！");
        }
        return rename(newFileName, username, fileDocument.getId());
    }

    @Override
    public ResponseResult<Object> addFile(String fileName, Boolean isFolder, String username, String parentPath) {
        String userId = userService.getUserIdByUserName(username);
        if (StringUtils.isEmpty(userId)) {
            ResultUtil.error("不存在的用户");
        }
        Path path = Paths.get(filePropertie.getRootDir(), username, parentPath, fileName);
        if (Files.exists(path)) {
            ResultUtil.warning("该文件已存在");
        }
        try {
            if (isFolder) {
                Files.createDirectories(path);
            } else {
                Files.createFile(path);
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return ResultUtil.error("新建文件失败");
        }
        String resPath = path.subpath(Paths.get(filePropertie.getRootDir(), username).getNameCount(), path.getNameCount()).toString();
        FileDocument fileDocument = new FileDocument();
        fileDocument.setName(fileName);
        fileDocument.setUserId(userId);
        fileDocument.setPath(resPath);
        fileDocument.setIsFolder(isFolder);
        fileDocument.setSuffix(FileUtil.extName(fileName));
        createFile(username, path.toFile());
        return ResultUtil.success(fileDocument);
    }

    @Override
    public String viewFile(String fileId, String operation) {
        FileDocument fileDocument = getById(fileId);
        String username = userService.getUserNameById(fileDocument.getUserId());
        String relativepath = org.apache.catalina.util.URLEncoder.DEFAULT.encode(fileDocument.getPath() + fileDocument.getName(), StandardCharsets.UTF_8);
        return "redirect:/file/"+ username + relativepath + "?o=" + operation;
    }

    @Override
    public String publicViewFile(String relativePath, String userId) {
        String username = userService.getUserNameById(userId);
        String userDirectory = getUserFilePath(aes.decryptStr(relativePath));
        return "redirect:/file/"+ username + userDirectory;
    }

    /***
     * 获取目录下的文件
     * @param username
     * @param dirPath
     * @param tempDir
     * @return
     */
    private List<FileDocument> listfile(String username, String dirPath, boolean tempDir) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        File[] fileList = dir.listFiles();
        if (fileList == null) {
            return Lists.newArrayList();
        }
        return Arrays.stream(fileList).map(file -> {
            FileDocument fileDocument = new FileDocument();
            String filename = file.getName();
            String suffix = FileUtil.extName(filename);
            boolean isFolder = file.isDirectory();
            fileDocument.setName(filename);
            fileDocument.setIsFolder(isFolder);
            fileDocument.setSuffix(suffix);
            fileDocument.setContentType(FileContentTypeUtils.getContentType(suffix));
            String path;
            Path dirPaths = Paths.get(file.getPath());
            if (tempDir) {
                path = dirPaths.subpath(Paths.get(filePropertie.getRootDir(), filePropertie.getChunkFileDir(), username).getNameCount(), dirPaths.getNameCount()).toString();
            } else {
                path = dirPaths.subpath(Paths.get(filePropertie.getRootDir(), username).getNameCount(), dirPaths.getNameCount()).toString();
            }
            fileDocument.setPath(path);
            return fileDocument;
        }).sorted(this::compareByFileName).collect(toList());
    }

    /***
     * 根据username 和 fileDocument 获取FilePath
     * @param username
     * @param fileDocument
     * @return
     * @throws CommonException
     */
    private String getFilePathByFileId(String username, FileDocument fileDocument) throws CommonException {
        StringBuilder sb = new StringBuilder();
        sb.append(filePropertie.getRootDir()).append(File.separator).append(username).append(getUserDirectory(fileDocument.getPath())).append(fileDocument.getName());
        Path path = Paths.get(sb.toString());
        if (!Files.exists(path)) {
            throw new CommonException(ExceptionType.DIR_NOT_FIND);
        }

        return sb.toString();
    }

    /***
     * 复制文件
     * @param upload
     * @param from
     * @param to
     * @return
     */
    private ResponseResult<Object> copy(UploadApiParamDTO upload, String from, String to) {
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

    private static String replaceStart(String str, CharSequence searchStr, CharSequence replacement) {
        return replacement + str.substring(searchStr.length());
    }

    /***
     * 文件重命名
     * @param newFileName
     * @param id
     * @param filePath
     * @param file
     * @return
     */
    private boolean renameFile(String newFileName, String id, String filePath, File file) {
        if (file.renameTo(new File(filePath + newFileName))) {
            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(id));
            Update update = new Update();
            update.set("name", newFileName);
            update.set("suffix", FileUtil.extName(newFileName));
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
    public ResponseResult<Object> upload(UploadApiParamDTO upload) throws IOException {
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
            File chunkFile = new File(filePropertie.getRootDir() + File.separator + upload.getUsername() + userDirectoryFilePath);
            // 保存文件信息
            upload.setInputStream(file.getInputStream());
            upload.setContentType(file.getContentType());
            upload.setSuffix(FileUtil.extName(filename));
            FileUtil.writeFromStream(file.getInputStream(), chunkFile);
            if (!filePropertie.getMonitor() || filePropertie.getTimeInterval() >= 3L) {
                createFile(upload.getUsername(), chunkFile);
            }
            uploadResponse.setUpload(true);
        } else {
            // 多个分片
            // 落地保存文件
            // 这时保存的每个块, 块先存好, 后续会调合并接口, 将所有块合成一个大文件
            // 保存在用户的tmp目录下
            StringBuilder sb = new StringBuilder();
            sb.append(filePropertie.getRootDir()).append(File.separator)
                    .append(filePropertie.getChunkFileDir()).append(File.separator)
                    .append(upload.getUsername()).append(File.separatorChar)
                    .append(md5).append(File.separatorChar).append(upload.getChunkNumber());
            File chunkFile = new File(sb.toString());
            FileUtil.writeFromStream(file.getInputStream(), chunkFile);
            setResumeCache(upload);
            uploadResponse.setUpload(true);
            // 检测是否已经上传完了所有分片,上传完了则需要合并
            if (checkIsNeedMerge(upload)) {
                uploadResponse.setMerge(true);
            }
            // 追加分片
            appendChunkFile(upload);
        }
        return ResultUtil.success(uploadResponse);
    }

    /***
     * 合并文件追加分片
     * @param upload
     */
    private void appendChunkFile(UploadApiParamDTO upload) {
        int chunkNumber = upload.getChunkNumber();
        String md5 = upload.getIdentifier();
        // 未写入的分片
        CopyOnWriteArrayList<Integer> unWrittenChunks = unWrittenCache.get(md5, key -> new CopyOnWriteArrayList<>());
        if (unWrittenChunks != null && !unWrittenChunks.contains(chunkNumber)) {
            unWrittenChunks.add(chunkNumber);
            unWrittenCache.put(md5, unWrittenChunks);
        }
        // 以写入的分片
        CopyOnWriteArrayList<Integer> writtenChunks = writtenCache.get(md5, key -> new CopyOnWriteArrayList<>());
        Path filePath = Paths.get(filePropertie.getRootDir(), filePropertie.getChunkFileDir(), upload.getUsername(), upload.getFilename());
        Lock lock = chunkWriteLockCache.get(md5, key -> new ReentrantLock());
        lock.lock();
        try {
            if (Files.exists(filePath) && writtenChunks.size() > 0) {
                // 继续追加
                for (int unWrittenChunk : unWrittenChunks) {
                    appenFile(upload, unWrittenChunks, writtenChunks);
                }
            } else {
                // 首次写入
                if (Files.exists(filePath)) {
                    Files.delete(filePath);
                }
                appenFile(upload, unWrittenChunks, writtenChunks);
            }
        } catch (Exception e) {
            throw new CommonException(ExceptionType.FAIL_MERGA_FILE);
        } finally {
            lock.unlock();
        }
    }

    /***
     * 追加分片操作
     * @param upload
     * @param unWrittenChunks
     * @param writtenChunks
     */
    private void appenFile(UploadApiParamDTO upload, CopyOnWriteArrayList<Integer> unWrittenChunks, CopyOnWriteArrayList<Integer> writtenChunks) {
        // 需要继续追加分片索引
        int chunk = 1;
        if (writtenChunks.size() > 0) {
            chunk = writtenChunks.get(writtenChunks.size() - 1) + 1;
        }
        if (!unWrittenChunks.contains(chunk)) {
            return;
        }
        String md5 = upload.getIdentifier();
        // 分片文件
        File file = Paths.get(filePropertie.getRootDir(), filePropertie.getChunkFileDir(), upload.getUsername(), md5, chunk + "").toFile();
        // 目标文件
        File outputFile = Paths.get(filePropertie.getRootDir(), filePropertie.getChunkFileDir(), upload.getUsername(), upload.getFilename()).toFile();
        long postion = outputFile.length();
        long count = file.length();
        try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile, true);
             FileChannel outChannel = fileOutputStream.getChannel()) {
            try (FileInputStream fileInputStream = new FileInputStream(file);
                 FileChannel inChannel = fileInputStream.getChannel()) {
                ByteBuffer byteBuffer = ByteBuffer.wrap(FileUtil.readBytes(file));
                outChannel.write(byteBuffer, postion);
                writtenChunks.add(chunk);
                writtenCache.put(md5, writtenChunks);
                unWrittenChunks.remove(unWrittenChunks.indexOf(chunk));
                unWrittenCache.put(md5, unWrittenChunks);
            }
        } catch (IOException e) {
            throw new CommonException(ExceptionType.FAIL_MERGA_FILE);
        }
    }

    @Override
    public ResponseResult<Object> uploadFolder(UploadApiParamDTO upload) throws CommonException {
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        // 新建文件夹
        String userDirectoryFilePath = getUserDirectoryFilePath(upload);
        //没有分片,直接存
        File dir = new File(filePropertie.getRootDir() + File.separator + upload.getUsername() + userDirectoryFilePath);
        if (!dir.exists()) {
            FileUtil.mkdir(dir);
        }
        // 保存文件夹信息
        if (!filePropertie.getMonitor() || filePropertie.getTimeInterval() >= 3L) {
            createFile(upload.getUsername(), dir);
        }
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> newFolder(UploadApiParamDTO upload) throws CommonException {
        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        // 新建文件夹
        String userDirectoryFilePath = getUserDirectoryFilePath(upload);
        File dir = new File(filePropertie.getRootDir() + File.separator + upload.getUsername() + userDirectoryFilePath);

        FileDocument fileDocument = new FileDocument();
        fileDocument.setIsFolder(true);
        fileDocument.setName(upload.getFilename());

        String path = getUserDirectory(upload.getCurrentDirectory());
        fileDocument.setPath(path);

        fileDocument.setUserId(upload.getUserId());
        fileDocument.setUploadDate(date);
        fileDocument.setUpdateDate(date);
        if (!dir.exists()) {
            FileUtil.mkdir(dir);
        }
        if (!filePropertie.getMonitor() || filePropertie.getTimeInterval() >= 3L) {
            createFile(upload.getUsername(), dir);
        }
        return ResultUtil.success();
    }

    /***
     * 保存文件信息
     * @param upload
     * @param md5
     * @param date
     */
    private FileDocument saveFileInfo(UploadApiParamDTO upload, String md5, LocalDateTime date) throws IOException {
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
            thumbnail.size(256, 256);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                thumbnail.toOutputStream(out);
                fileDocument.setContent(out.toByteArray());
            } catch (UnsupportedFormatException e) {
                log.warn(e.getMessage());
            }
        }
        return saveFileInfo(fileDocument);
    }

    private FileDocument saveFileInfo(FileDocument fileDocument) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(fileDocument.getUserId()));
        query.addCriteria(Criteria.where("isFolder").is(fileDocument.getIsFolder()));
        query.addCriteria(Criteria.where("path").is(fileDocument.getPath()));
        query.addCriteria(Criteria.where("name").is(fileDocument.getName()));
        FileDocument res = mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
        if (res != null) {
            Update update = new Update();
            update.set("size", fileDocument.getSize());
            update.set("md5", fileDocument.getMd5());
            mongoTemplate.upsert(query, update, COLLECTION_NAME);
            res.setSize(fileDocument.getSize());
            res.setMd5(fileDocument.getMd5());
        } else {
            return mongoTemplate.save(fileDocument, COLLECTION_NAME);
        }
        return res;
    }

    /***
     * 替换markdown中的图片url
     * @param input
     * @return
     */
    public static String replaceAll(CharSequence input, String path, String userId) throws CommonException {
        Pattern pattern = Pattern.compile("!\\[(.*)]\\((.*)\\)");
        Pattern pattern1 = Pattern.compile("(?<=]\\()[^)]+");
        Matcher matcher = pattern.matcher(input).usePattern(pattern1);
        matcher.reset();
        boolean result = matcher.find();
        if (result) {
            StringBuffer sb = new StringBuffer();
            do {
                //"/public/view?relativePath="+path + oldSrc +"&userId="+userId;
                String value = matcher.group(0);
                if (value.matches("(?!([hH][tT]{2}[pP]:/*|[hH][tT]{2}[pP][sS]:/*|[fF][tT][pP]:/*)).*?$+") && !value.startsWith("/file/public/image")) {
                    String relativepath = aes.encryptBase64(path + value);
                    try {
                        relativepath = URLEncoder.encode(relativepath, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        throw new CommonException(-1, e.getMessage());
                    }
                    String replacement = "/api/public/view?relativePath=" + relativepath + "&userId=" + userId;
                    matcher.appendReplacement(sb, replacement);
                }
                result = matcher.find();
            } while (result);
            matcher.appendTail(sb);
            return sb.toString();
        }
        return input.toString();
    }

    /***
     * 部分文件信息
     * @param upload
     * @param date
     * @param fileDocument
     */
    private void saveFileInfo(UploadApiParamDTO upload, LocalDateTime date, FileDocument fileDocument) {
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
    private void saveFolderInfo(UploadApiParamDTO upload, LocalDateTime date) {
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

    /***
     * 缓存以上传的分片
     * @param upload
     */
    private void setResumeCache(UploadApiParamDTO upload) {
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
    private CopyOnWriteArrayList<Integer> getSavedChunk(UploadApiParamDTO upload) {
        String md5 = upload.getIdentifier();
        return resumeCache.get(md5, key -> createResumeCache(upload));
    }

    /***
     * 检测是否需要合并
     */
    private boolean checkIsNeedMerge(UploadApiParamDTO upload) {
        int totalChunks = upload.getTotalChunks();
        CopyOnWriteArrayList<Integer> chunkList = getSavedChunk(upload);
        return totalChunks == chunkList.size();
    }

    /***
     * 读取分片文件是否存在
     * @return
     */
    private CopyOnWriteArrayList<Integer> createResumeCache(UploadApiParamDTO upload) {
        CopyOnWriteArrayList<Integer> resumeList = new CopyOnWriteArrayList<>();
        String md5 = upload.getIdentifier();
        // 读取tmp分片目录所有文件
        File f = new File(filePropertie.getRootDir() + File.separator + filePropertie.getChunkFileDir() + File.separator + upload.getUsername() + File.separator + md5);
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

    @Override
    public ResponseResult<Object> checkChunkUploaded(UploadApiParamDTO upload) throws IOException {
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

    @Override
    public ResponseResult<Object> merge(UploadApiParamDTO upload) throws IOException {
        UploadResponse uploadResponse = new UploadResponse();
        String md5 = upload.getIdentifier();
        File file = Paths.get(filePropertie.getRootDir(), filePropertie.getChunkFileDir(), upload.getUsername(), upload.getFilename()).toFile();
        File outputFile = Paths.get(filePropertie.getRootDir(), upload.getUsername(), getUserDirectoryFilePath(upload)).toFile();
        // 清除缓存
        resumeCache.invalidate(md5);
        writtenCache.invalidate(md5);
        unWrittenCache.invalidate(md5);
        chunkWriteLockCache.invalidate(md5);
        File chunkDir = Paths.get(filePropertie.getRootDir(), filePropertie.getChunkFileDir(), upload.getUsername(), md5).toFile();
        FileUtil.del(chunkDir);
        FileUtil.move(file, outputFile, true);
        uploadResponse.setUpload(true);
        if (!filePropertie.getMonitor() || filePropertie.getTimeInterval() >= 3L) {
            createFile(upload.getUsername(), outputFile);
        }
        return ResultUtil.success(uploadResponse);
    }

    /***
     * 用户磁盘目录
     * @param upload
     * @return String
     */
    private String getUserDirectoryFilePath(UploadApiParamDTO upload) {
        String currentDirectory = upload.getCurrentDirectory();
        if (StringUtils.isEmpty(currentDirectory)) {
            currentDirectory = filePropertie.getSeparator();
        }
        if (upload.getIsFolder()) {
            if (upload.getFolderPath() != null) {
                currentDirectory += filePropertie.getSeparator() + upload.getFolderPath();
            } else {
                currentDirectory += filePropertie.getSeparator() + upload.getFilename();
            }
        } else {
            currentDirectory += filePropertie.getSeparator() + upload.getRelativePath();
        }
        currentDirectory = currentDirectory.replaceAll(filePropertie.getSeparator(), File.separator);
        return currentDirectory;
    }

    /***
     * 用户当前目录(跨平台)
     * @param currentDirectory 当前目录
     * @return
     */
    private String getUserDirectory(String currentDirectory) {
        if (StringUtils.isEmpty(currentDirectory)) {
            currentDirectory = filePropertie.getSeparator();
        } else {
            if (!currentDirectory.endsWith(filePropertie.getSeparator())) {
                currentDirectory += filePropertie.getSeparator();
            }
        }
        currentDirectory = currentDirectory.replaceAll(filePropertie.getSeparator(), File.separator);
        return currentDirectory;
    }

    /***
     * 用户文件路径(相对路径)
     * @param relativePath
     * @return
     */
    private String getUserFilePath(String relativePath) {
        if (!StringUtils.isEmpty(relativePath)) {
            relativePath = relativePath.replaceAll(filePropertie.getSeparator(), File.separator);
        }
        return relativePath;
    }

    @Override
    public ResponseResult<Object> favorite(List<String> fileIds) throws CommonException {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        Update update = new Update();
        update.set("isFavorite", true);
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> unFavorite(List<String> fileIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        Update update = new Update();
        update.set("isFavorite", false);
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> delete(String username, List<String> fileIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        List<FileDocument> fileDocuments = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        boolean isDel = false;
        for (FileDocument fileDocument : fileDocuments) {
            String currentDirectory = getUserDirectory(fileDocument.getPath());
            String filePath = filePropertie.getRootDir() + File.separator + username + currentDirectory + fileDocument.getName();
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

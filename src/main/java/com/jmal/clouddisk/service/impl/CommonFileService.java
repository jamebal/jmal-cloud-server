package com.jmal.clouddisk.service.impl;

import cn.hutool.core.io.CharsetDetector;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.lucene.EtagService;
import com.jmal.clouddisk.lucene.LuceneIndexQueueEvent;
import com.jmal.clouddisk.media.VideoProcessService;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.file.FileBase;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileIntroVO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileVersionService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.FileContentTypeUtils;
import com.jmal.clouddisk.util.MyFileUtils;
import com.jmal.clouddisk.util.TimeUntils;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.jmal.clouddisk.service.Constants.UPDATE_DATE;
import static com.jmal.clouddisk.service.IUserService.USER_ID;

/**
 * @author jmal
 * @Description CommonFileService
 * @date 2023/4/7 17:27
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommonFileService {

    private final UserLoginHolder userLoginHolder;

    private final FileService fileService;

    private final CommonUserFileService commonUserFileService;

    private final MessageService messageService;

    private final CommonUserService userService;

    public static final String COLLECTION_NAME = "fileDocument";

    public static final String TRASH_COLLECTION_NAME = "trash";

    private final MongoTemplate mongoTemplate;

    private final FileProperties fileProperties;

    private final VideoProcessService videoProcessService;

    private final IFileVersionService fileVersionService;

    private final ApplicationEventPublisher eventPublisher;

    private final EtagService etagService;

    protected static final Set<String> FILE_PATH_LOCK = new CopyOnWriteArraySet<>();

    public ResponseEntity<Object> getObjectResponseEntity(FileDocument fileDocument) {
        if (fileDocument != null) {
            return ResponseEntity.ok().header(HttpHeaders.CONTENT_TYPE, fileDocument.getContentType()).header(HttpHeaders.CONNECTION, "close").header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileDocument.getContent() != null ? fileDocument.getContent().length : 0)).header(HttpHeaders.CONTENT_ENCODING, "utf-8").header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800").body(fileDocument.getContent());
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到该文件");
        }
    }


    /***
     * 用户当前目录(跨平台)
     * @param currentDirectory 当前目录
     * @return 用户当前目录
     */
    public String getUserDirectory(String currentDirectory) {
        if (CharSequenceUtil.isBlank(currentDirectory)) {
            currentDirectory = fileProperties.getSeparator();
        } else {
            if (!currentDirectory.endsWith(fileProperties.getSeparator())) {
                currentDirectory += fileProperties.getSeparator();
            }
        }
        return currentDirectory;
    }

    /**
     * 是否存在该文件
     *
     * @param path      文件的相对路径
     * @param userId    userId 用户Id
     * @param filenames 文件名列表
     * @return FileDocument
     */
    FileDocument exist(String path, String userId, List<String> filenames) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("path").is(path));
        query.addCriteria(Criteria.where("name").in(filenames));
        return mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
    }

    /**
     * 是否存在该文件
     *
     * @param path   文件的相对路径
     * @param userId userId
     * @param md5    md5
     * @return FileDocument
     */
    FileDocument getByMd5(String path, String userId, String md5) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("md5").is(md5));
        query.addCriteria(Criteria.where("path").is(path));
        return mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
    }

    public static Query getQuery(FileDocument fileDocument) {
        return getQuery(fileDocument.getPath(), fileDocument.getName(), fileDocument.getUserId());
    }

    public static Query getQuery(String path, String name, String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("name").is(name));
        query.addCriteria(Criteria.where("path").is(path));
        return query;
    }

    public FileDocument getFileDocumentById(String fileId, boolean excludeContent) {
        return fileService.getFileDocumentById(fileId, excludeContent);
    }

    public FileDocument getById(String fileId) {
        return getById(fileId, true);
    }

    public FileDocument getById(String fileId, boolean excludeContent) {
        return fileService.getById(fileId, excludeContent);
    }

    public FileDocument getFileDocumentByOssPath(String ossPath, String pathName) {
        return fileService.getFileDocumentByOssPath(ossPath, pathName);
    }

    /**
     * 统计文件夹的大小
     */
    public long getFolderSize(String collectionName, String userId, String path) {
        return etagService.getFolderSize(collectionName, userId, path);
    }

    public FileDocument getFileDocument(String userId, String fileName, String relativePath, Query query) {
        return commonUserFileService.getFileDocument(userId, fileName, relativePath, query);
    }

    public FileDocument getFileDocument(String username, String fileAbsolutePath, Query query) {
        String userId = userService.getUserIdByUserName(username);
        if (CharSequenceUtil.isBlank(userId)) {
            return null;
        }
        File file = new File(fileAbsolutePath);
        if (!file.exists()) {
            return null;
        }
        String fileName = file.getName();
        String relativePath = fileAbsolutePath.substring(fileProperties.getRootDir().length() + username.length() + 1, fileAbsolutePath.length() - fileName.length());
        return getFileDocument(userId, fileName, relativePath, query);
    }

    public FileDocument getFileDocument(String userId, String fileName, String relativePath) {
        Query query = new Query();
        // 文件是否存在
        return getFileDocument(userId, fileName, relativePath, query);
    }

    public static String getContentType(File file, String contentType) {
        try {
            if (MyFileUtils.hasCharset(file)) {
                String charset = UniversalDetector.detectCharset(file);
                if (StrUtil.isNotBlank(charset)) {
                    if (StandardCharsets.UTF_8.name().equals(charset)) {
                        if (FileContentTypeUtils.DEFAULT_CONTENT_TYPE.equals(contentType)) {
                            contentType = "text/plan;charset=utf-8";
                        } else {
                            contentType = contentType + ";charset=utf-8";
                        }
                    } else {
                        if (file.length() < FileContentTypeUtils.MAX_DETECT_FILE_SIZE && CharsetDetector.detect(file).equals(StandardCharsets.UTF_8)) {
                            if (FileContentTypeUtils.DEFAULT_CONTENT_TYPE.equals(contentType)) {
                                contentType = "text/plan;charset=utf-8";
                            } else {
                                contentType = contentType + ";charset=utf-8";
                            }
                        } else {
                            contentType = contentType + ";charset=" + charset;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return contentType;
    }

    public void pushMessageOperationFileError(String username, String message, String operation) {
        JSONObject msg = new JSONObject();
        msg.put("code", -1);
        msg.put("msg", message);
        msg.put("operation", operation);
        messageService.pushMessage(username, msg, Constants.OPERATION_FILE);
    }

    public void pushMessageOperationFileSuccess(String fromPath, String toPath, String username, String operation) {
        JSONObject msg = new JSONObject();
        msg.put("code", 0);
        msg.put("from", fromPath);
        msg.put("to", toPath);
        msg.put("operation", operation);
        messageService.pushMessage(username, msg, Constants.OPERATION_FILE);
    }

    /***
     * 设置共享属性
     * @param fileDocument FileDocument
     * @param expiresAt 过期时间
     * @param query 查询条件
     */
    void setShareAttribute(FileDocument fileDocument, long expiresAt, ShareDO share, Query query) {
        Update update = new Update();
        setShareAttribute(update, expiresAt, share.getId(), share.getIsPrivacy(), share.getExtractionCode(), share.getOperationPermissionList());
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
        // 修改第一个文件/文件夹
        updateShareFirst(fileDocument, update, true);
    }

    private void updateShareFirst(FileDocument fileDocument, Update update, boolean share) {
        if (share) {
            update.set(Constants.SHARE_BASE, true);
        } else {
            update.unset(Constants.SHARE_BASE);
        }
        Query query1 = new Query();
        query1.addCriteria(Criteria.where("_id").is(fileDocument.getId()));
        mongoTemplate.updateFirst(query1, update, COLLECTION_NAME);
    }

    /**
     * 设置共享属性
     *
     * @param expiresAt      过期时间
     * @param shareId        shareId
     * @param isPrivacy      isPrivacy
     * @param extractionCode extractionCode
     */
    public static void setShareAttribute(Update update, long expiresAt, String shareId, Boolean isPrivacy, String extractionCode, List<OperationPermission> operationPermissionListList) {
        update.set(Constants.IS_SHARE, true);
        update.set(Constants.SHARE_ID, shareId);
        update.set(Constants.EXPIRES_AT, expiresAt);
        update.set(Constants.IS_PRIVACY, isPrivacy);
        if (operationPermissionListList != null) {
            update.set(Constants.OPERATION_PERMISSION_LIST, operationPermissionListList);
        }
        if (BooleanUtil.isTrue(isPrivacy)) {
            update.set(Constants.EXTRACTION_CODE, extractionCode);
        } else {
            update.unset(Constants.EXTRACTION_CODE);
        }
    }

    /***
     * 解除共享属性
     * @param fileDocument FileDocument
     * @param query 查询条件
     */
    void unsetShareAttribute(FileDocument fileDocument, Query query) {
        Update update = new Update();
        update.unset(Constants.SHARE_ID);
        update.unset(Constants.IS_SHARE);
        update.unset(Constants.SUB_SHARE);
        update.unset(Constants.EXPIRES_AT);
        update.unset(Constants.IS_PRIVACY);
        update.unset(Constants.OPERATION_PERMISSION_LIST);
        update.unset(Constants.EXTRACTION_CODE);
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
        // 修改第一个文件/文件夹
        updateShareFirst(fileDocument, update, false);
    }

    public void checkPermissionUsername(String username, String currentUsername, List<OperationPermission> operationPermissionList, OperationPermission operationPermission) {
        if (!username.equals(currentUsername) && noPermission(operationPermissionList, operationPermission)) {
            throw new CommonException(ExceptionType.PERMISSION_DENIED);
        }
    }

    public void checkPermissionUsername(String username, List<OperationPermission> operationPermissionList, OperationPermission operationPermission) {
        if (!username.equals(userLoginHolder.getUsername()) && noPermission(operationPermissionList, operationPermission)) {
            throw new CommonException(ExceptionType.PERMISSION_DENIED);
        }
    }

    public void checkPermissionUserId(String userId, List<OperationPermission> operationPermissionList, OperationPermission operationPermission) {
        if (!userId.equals(userLoginHolder.getUserId()) && noPermission(operationPermissionList, operationPermission)) {
            throw new CommonException(ExceptionType.PERMISSION_DENIED);
        }
    }

    private static boolean noPermission(List<OperationPermission> operationPermissionList, OperationPermission operationPermission) {
        return operationPermissionList == null || !operationPermissionList.contains(operationPermission);
    }

    public void deleteDependencies(String username, List<String> fileIds, boolean sweep) {
        if (sweep) {
            // delete history version
            fileVersionService.deleteAll(fileIds);
            // delete video cache
            videoProcessService.deleteVideoCacheByIds(username, fileIds);
        }
        // delete share
        Query shareQuery = new Query();
        shareQuery.addCriteria(Criteria.where(Constants.FILE_ID).in(fileIds));
        mongoTemplate.remove(shareQuery, ShareDO.class);
        // delete index
        eventPublisher.publishEvent(new LuceneIndexQueueEvent(this, fileIds));
    }

    public Query getAllByFolderQuery(FileDocument fileDocument) {
        Query query1 = new Query();
        query1.addCriteria(Criteria.where(USER_ID).is(fileDocument.getUserId()));
        query1.addCriteria(Criteria.where("path").regex("^" + ReUtil.escape(fileDocument.getPath() + fileDocument.getName() + "/")));
        return query1;
    }

    public void deleteFile(String username, File file) {
        String fileAbsolutePath = file.getAbsolutePath();
        String fileName = file.getName();
        String relativePath = fileAbsolutePath.substring(fileProperties.getRootDir().length() + username.length() + 1, fileAbsolutePath.length() - fileName.length());
        String userId = userService.getUserIdByUserName(username);
        if (CharSequenceUtil.isBlank(userId)) {
            return;
        }
        Query query = new Query();
        // 文件是否存在
        FileDocument fileDocument = getFileDocument(userId, fileName, relativePath, query);
        if (fileDocument != null) {
            deleteDependencies(username, Collections.singletonList(fileDocument.getId()), false);
            mongoTemplate.remove(query, COLLECTION_NAME);
            if (BooleanUtil.isTrue(fileDocument.getIsFolder())) {
                // 删除文件夹及其下的所有文件
                mongoTemplate.remove(getAllByFolderQuery(fileDocument), FileDocument.class);
                eventPublisher.publishEvent(new LuceneIndexQueueEvent(this, Collections.singletonList(fileDocument.getId())));
            }
        }
        messageService.pushMessage(username, relativePath, Constants.DELETE_FILE);
    }

    public void modifyFile(String username, File file) {
        // 判断文件是否存在
        if (!file.exists()) {
            return;
        }
        String fileAbsolutePath = file.getAbsolutePath();
        String fileName = file.getName();
        String relativePath = fileAbsolutePath.substring(fileProperties.getRootDir().length() + username.length() + 1, fileAbsolutePath.length() - fileName.length());
        String userId = userService.getUserIdByUserName(username);
        if (CharSequenceUtil.isBlank(userId)) {
            return;
        }
        Query query = new Query();
        // 文件是否存在
        FileDocument fileDocument = getFileDocument(userId, fileName, relativePath, query);

        String suffix = MyFileUtils.extName(file.getName());
        String contentType = FileContentTypeUtils.getContentType(file, suffix);
        // 文件是否存在
        if (fileDocument != null) {
            Update update = new Update();
            update.set("size", file.length());
            update.set("md5", file.length() + "/" + fileDocument.getName());
            update.set(Constants.SUFFIX, suffix);
            String fileContentType = getContentType(file, contentType);
            update.set(Constants.CONTENT_TYPE, fileContentType);
            LocalDateTime updateTime = CommonUserFileService.getFileLastModifiedTime(file);
            update.set(UPDATE_DATE, updateTime);
            // 如果size相同，不更新,且更新时间在1秒内,则不更新
            if (TimeUntils.isWithinOneSecond(fileDocument.getUpdateDate(), updateTime)) {
                return;
            }
            UpdateResult updateResult = mongoTemplate.upsert(query, update, COLLECTION_NAME);
            fileDocument.setSize(file.length());
            fileDocument.setUpdateDate(updateTime);
            if (contentType.contains(Constants.CONTENT_TYPE_MARK_DOWN) || "md".equals(suffix)) {
                // 写入markdown内容
                String markDownContent = FileUtil.readString(file, MyFileUtils.getFileCharset(file));
                update.set("contentText", markDownContent);
            }
            messageService.pushMessage(username, fileDocument, Constants.UPDATE_FILE);
            if (updateResult.getModifiedCount() > 0) {
                eventPublisher.publishEvent(new LuceneIndexQueueEvent(this, fileDocument.getId()));
            }
            // 判断文件类型中是否包含utf-8
            if (fileContentType.contains("utf-8") || fileContentType.contains("office")) {
                // 修改文件之后保存历史版本
                fileVersionService.saveFileVersion(username, Paths.get(relativePath, file.getName()).toString(), userId);
            }
        } else {
            commonUserFileService.createFile(username, file, userId, null);
        }
    }

    public List<FileIntroVO> sortByFileName(UploadApiParamDTO upload, List<FileIntroVO> fileIntroVOList, String order) {
        // 按文件名排序
        if (CharSequenceUtil.isBlank(order)) {
            fileIntroVOList = fileIntroVOList.stream().sorted(this::compareByFileName).toList();
        }
        if (!CharSequenceUtil.isBlank(order) && "name".equals(upload.getSortableProp())) {
            fileIntroVOList = fileIntroVOList.stream().sorted(this::compareByFileName).toList();
            if ("descending".equals(order)) {
                fileIntroVOList = fileIntroVOList.stream().sorted(this::desc).toList();
            }
        }
        return fileIntroVOList;
    }

    public int desc(FileBase f1, FileBase f2) {
        return -1;
    }

    /***
     * 根据文件名排序
     * @param f1 f1
     * @param f2 f2
     */
    public int compareByFileName(FileBase f1, FileBase f2) {
        if (Boolean.TRUE.equals(f1.getIsFolder()) && !f2.getIsFolder()) {
            return -1;
        } else if (f1.getIsFolder() && f2.getIsFolder()) {
            return compareByName(f1, f2);
        } else if (!f1.getIsFolder() && Boolean.TRUE.equals(f2.getIsFolder())) {
            return 1;
        } else {
            return compareByName(f1, f2);
        }
    }

    public int compareByName(FileBase f1, FileBase f2) {
        Comparator<Object> cmp = Collator.getInstance(java.util.Locale.CHINA);
        return cmp.compare(f1.getName(), f2.getName());
    }

    /**
     * 按文件大小倒叙排列
     */
    public int compareBySizeDesc(FileBase f1, FileBase f2) {
        return f2.getSize() - f1.getSize() > 0 ? 1 : -1;
    }

    /**
     * 按文件大小正叙排列
     */
    public int compareBySize(FileBase f1, FileBase f2) {
        return f1.getSize() - f2.getSize() > 0 ? 1 : -1;
    }

    /**
     * 按最近修改时间倒叙排列
     */
    public int compareByUpdateDateDesc(FileBase f1, FileBase f2) {
        return f1.getUpdateDate().compareTo(f2.getUpdateDate());
    }

    /**
     * 按最近修改时间正叙排列
     */
    public int compareByUpdateDate(FileBase f1, FileBase f2) {
        return f2.getUpdateDate().compareTo(f1.getUpdateDate());
    }

    public static boolean isLock(FileDocument fileDocument) {
        String filePath = getLockFilePath(fileDocument);
        // 完全匹配
        return isLock(filePath);
    }

    public static boolean isLock(File file, String rootDir, String username) {
        String filePath = getLockFilePath(file, rootDir, username);
        return isLock(filePath);
    }

    public static void lockFile(FileDocument fileDocument) {
        String filePath = getLockFilePath(fileDocument);
        CommonFileService.FILE_PATH_LOCK.add(getLockFilePath(fileDocument));
        log.info("lock file path: {}", filePath);
    }

    public static void unLockFile(FileDocument fileDocument) {
        CommonFileService.FILE_PATH_LOCK.remove(getLockFilePath(fileDocument));
    }

    private static boolean isLock(String filePath) {
        // 完全匹配
        if (CommonFileService.FILE_PATH_LOCK.contains(filePath)) {
            return true;
        }
        // 前缀匹配
        return CommonFileService.FILE_PATH_LOCK.stream().anyMatch(filePath::startsWith);
    }

    private static String getLockFilePath(FileDocument fileDocument) {
        return fileDocument.getPath() + fileDocument.getName() + (Boolean.TRUE.equals(fileDocument.getIsFolder()) ? MyWebdavServlet.PATH_DELIMITER : "");
    }

    private static String getLockFilePath(File file, String rooDir, String username) {
        Path absolutePath = file.toPath();
        Path relativePath = absolutePath.subpath(Paths.get(rooDir, username).getNameCount(), absolutePath.getNameCount());
        return MyWebdavServlet.PATH_DELIMITER + relativePath + (file.isDirectory() ? MyWebdavServlet.PATH_DELIMITER : "");
    }

    public static void setPage(Integer pageSize, Integer pageIndex, Query query) {
        if (pageSize == null) {
            pageSize = 10;
        }
        if (pageIndex == null) {
            pageIndex = 1;
        }
        long skip = (long) (pageIndex - 1) * pageSize;
        query.skip(skip);
        query.limit(pageSize);
    }

    /**
     * 删除有删除标记的文档
     */
    public void deleteDocWithDeleteFlag() {
        try {
            boolean run = true;
            log.debug("开始删除有删除标记的文档");
            while (run) {
                Query query = new Query();
                query.addCriteria(Criteria.where("delete").is(1));
                long count = mongoTemplate.count(query, CommonFileService.COLLECTION_NAME);
                if (count == 0) {
                    run = false;
                }
                List<org.bson.Document> pipeline = Arrays.asList(new org.bson.Document("$match", new org.bson.Document("delete", 1)), new org.bson.Document("$project", new org.bson.Document("_id", 1).append("name", 1).append("path", 1).append("userId", 1)), new org.bson.Document("$sort", new org.bson.Document("isFolder", 1L)), new org.bson.Document("$limit", 1));
                AggregateIterable<org.bson.Document> aggregateIterable = mongoTemplate.getCollection(CommonFileService.COLLECTION_NAME).aggregate(pipeline);
                for (org.bson.Document document : aggregateIterable) {
                    Object ObjectId = document.get("_id");
                    String fileId = null;
                    if (ObjectId instanceof ObjectId) {
                        fileId = document.getObjectId("_id").toHexString();
                    }
                    if (ObjectId instanceof String) {
                        fileId = document.getString("_id");
                    }
                    String userId = document.getString("userId");
                    String username = userService.getUserNameById(userId);
                    String name = document.getString("name");
                    String path = document.getString("path");
                    File file = new File(Paths.get(fileProperties.getRootDir(), username, path, name).toString());
                    if (!file.exists() || fileProperties.getMonitorIgnoreFilePrefix().stream().anyMatch(file.getName()::startsWith)) {
                        deleteFile(username, file);
                        log.info("删除不存在的文档: {}", file.getAbsolutePath());
                    } else {
                        if (fileId != null) {
                            Query removeDeletequery = new Query();
                            removeDeletequery.addCriteria(Criteria.where("_id").in(fileId).and("delete").is(1));
                            Update update = new Update();
                            update.unset("delete");
                            UpdateResult result = mongoTemplate.updateMulti(removeDeletequery, update, CommonFileService.COLLECTION_NAME);
                            if (result.getModifiedCount() == 0) {
                                log.warn("Failed to unset delete flag for file: {}", file.getAbsolutePath());
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

}

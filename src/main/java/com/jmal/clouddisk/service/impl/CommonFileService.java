package com.jmal.clouddisk.service.impl;

import cn.hutool.core.io.CharsetDetector;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.dao.IShareDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.lucene.EtagService;
import com.jmal.clouddisk.lucene.LuceneIndexQueueEvent;
import com.jmal.clouddisk.media.VideoProcessService;
import com.jmal.clouddisk.model.OperationPermission;
import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.model.file.FileBase;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.ShareProperties;
import com.jmal.clouddisk.model.file.dto.FileBaseDTO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileVersionService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.FileContentTypeUtils;
import com.jmal.clouddisk.util.MyFileUtils;
import com.jmal.clouddisk.util.TimeUntils;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

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

    // private final MongoTemplate mongoTemplate;

    private final IFileDAO fileDAO;

    private final IShareDAO shareDAO;

    private final FileProperties fileProperties;

    private final VideoProcessService videoProcessService;

    private final IFileVersionService fileVersionService;

    private final ApplicationEventPublisher eventPublisher;

    private final EtagService etagService;

    protected static final Set<String> FILE_PATH_LOCK = new CopyOnWriteArraySet<>();

    public ResponseEntity<Object> getObjectResponseEntity(FileDocument fileDocument) {
        if (fileDocument != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, fileDocument.getContentType())
                    .header(HttpHeaders.CONNECTION, "close")
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileDocument.getContent() != null ? fileDocument.getContent().length : 0))
                    .header(HttpHeaders.CONTENT_ENCODING, "utf-8")
                    .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                    .body(fileDocument.getContent());
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

    public static Query getQuery(FileDocument fileDocument) {
        return getQuery(fileDocument.getUserId(), fileDocument.getPath(), fileDocument.getName());
    }

    public static Query getQuery(String userId, String path, String name) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where(Constants.PATH_FIELD).is(path));
        query.addCriteria(Criteria.where(Constants.FILENAME_FIELD).is(name));
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

    public FileBaseDTO getFileBaseDTO(String username, String fileAbsolutePath) {
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
        return new FileBaseDTO(fileName, relativePath, userId);
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
        msg.set("code", -1);
        msg.set("msg", message);
        msg.set("operation", operation);
        messageService.pushMessage(username, msg, Constants.OPERATION_FILE);
    }

    public void pushMessageOperationFileSuccess(String fromPath, String toPath, String username, String operation) {
        JSONObject msg = new JSONObject();
        msg.set("code", 0);
        msg.set("from", fromPath);
        msg.set("to", toPath);
        msg.set("operation", operation);
        messageService.pushMessage(username, msg, Constants.OPERATION_FILE);
    }

    /***
     * 设置共享属性
     * @param fileDocument FileDocument
     * @param expiresAt 过期时间
     * @param share ShareDO
     * @param isFolder 是否是文件夹
     */
    void setShareAttribute(FileDocument fileDocument, long expiresAt, ShareDO share, boolean isFolder) {
        ShareProperties shareProperties = new ShareProperties(share.getIsPrivacy(), share.getExtractionCode(), expiresAt, share.getOperationPermissionList(), true);
        fileDAO.updateShareProps(fileDocument, share.getId(), shareProperties, isFolder);
        // 修改第一个文件/文件夹
        fileDAO.updateShareFirst(fileDocument.getId(), true);
    }

    /***
     * 解除共享属性
     * @param fileDocument FileDocument
     * @param isFolder 是否是文件夹
     */
    void unsetShareAttribute(FileDocument fileDocument, boolean isFolder) {
        fileDAO.unsetShareProps(fileDocument, isFolder);
        // 修改第一个文件/文件夹
        fileDAO.updateShareFirst(fileDocument.getId(), false);
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
        shareDAO.removeByFileIdIn(fileIds);
        // delete index
        eventPublisher.publishEvent(new LuceneIndexQueueEvent(this, fileIds));
    }

    public void deleteFile(String username, File file) {
        String fileAbsolutePath = file.getAbsolutePath();
        String fileName = file.getName();
        String relativePath = fileAbsolutePath.substring(fileProperties.getRootDir().length() + username.length() + 1, fileAbsolutePath.length() - fileName.length());
        String userId = userService.getUserIdByUserName(username);
        if (CharSequenceUtil.isBlank(userId)) {
            return;
        }
        // 文件是否存在
        FileBaseDTO fileBaseDTO = fileDAO.findFileBaseDTOByUserIdAndPathAndName(userId, relativePath, fileName);
        if (fileBaseDTO != null) {
            deleteDependencies(username, Collections.singletonList(fileBaseDTO.getId()), false);
            fileDAO.removeByUserIdAndPathAndName(userId, relativePath, fileName);
            if (BooleanUtil.isTrue(fileBaseDTO.getIsFolder())) {
                // 删除文件夹及其下的所有文件
                fileDAO.removeAllByFolder(fileBaseDTO);
                eventPublisher.publishEvent(new LuceneIndexQueueEvent(this, Collections.singletonList(fileBaseDTO.getId())));
            }
        }
        // update parent folder etag
        etagService.handleItemDeletionAsync(username, file);
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
        // 文件是否存在
        FileDocument fileDocument = fileDAO.findByUserIdAndPathAndName(userId, relativePath, fileName);
        String suffix = MyFileUtils.extName(file.getName());
        String contentType = FileContentTypeUtils.getContentType(file, suffix);
        // 文件是否存在
        if (fileDocument != null) {
            LocalDateTime updateTime = CommonUserFileService.getFileLastModifiedTime(file);
            // 如果size相同，不更新,且更新时间在1秒内,则不更新
            if (TimeUntils.isWithinOneSecond(fileDocument.getUpdateDate(), updateTime)) {
                return;
            }
            String fileContentType = getContentType(file, contentType);
            String md5 = file.length() + "/" + fileDocument.getName();
            long modifiedCount = fileDAO.updateModifyFile(fileDocument.getId(), file.length(), md5, suffix, fileContentType, updateTime);
            // if (contentType.contains(Constants.CONTENT_TYPE_MARK_DOWN) || "md".equals(suffix)) {
            //     // 写入markdown内容
            //     String markDownContent = FileUtil.readString(file, MyFileUtils.getFileCharset(file));
            //     update.set(Constants.CONTENT_TEXT, markDownContent);
            // }
            fileDocument.setSize(file.length());
            fileDocument.setUpdateDate(updateTime);
            messageService.pushMessage(username, fileDocument, Constants.UPDATE_FILE);
            if (modifiedCount > 0) {
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

    public static boolean isLock(FileBaseDTO fileDocument) {
        String filePath = getLockFilePath(fileDocument);
        // 完全匹配
        return isLock(filePath);
    }

    public static boolean isLock(File file, String rootDir, String username) {
        String filePath = getLockFilePath(file, rootDir, username);
        return isLock(filePath);
    }

    public static void lockFile(FileBaseDTO fileDocument) {
        String filePath = getLockFilePath(fileDocument);
        CommonFileService.FILE_PATH_LOCK.add(getLockFilePath(fileDocument));
        log.info("lock file path: {}", filePath);
    }

    public static void unLockFile(FileBaseDTO fileDocument) {
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

    private static String getLockFilePath(FileBaseDTO fileDocument) {
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
                long count = fileDAO.countByDelTag(1);
                if (count == 0) {
                    run = false;
                }
                // List<org.bson.Document> pipeline = Arrays.asList(
                //         new org.bson.Document("$match", new org.bson.Document("delete", 1)),
                //         new org.bson.Document("$project", new org.bson.Document("_id", 1).append("name", 1).append("path", 1).append("userId", 1)),
                //         new org.bson.Document("$sort", new org.bson.Document("isFolder", 1L)),
                //         new org.bson.Document("$limit", 1));
                // AggregateIterable<org.bson.Document> aggregateIterable = mongoTemplate.getCollection(CommonFileService.COLLECTION_NAME).aggregate(pipeline);
                List<FileBaseDTO> fileBaseDTOList = fileDAO.findFileBaseDTOByDelTagOfLimit(1, 6);
                for (FileBaseDTO fileBaseDTO : fileBaseDTOList) {
                    String fileId = fileBaseDTO.getId();
                    String username = userService.getUserNameById(fileBaseDTO.getUserId());
                    File file = new File(Paths.get(fileProperties.getRootDir(), username, fileBaseDTO.getPath(), fileBaseDTO.getName()).toString());
                    if (!file.exists() || fileProperties.getMonitorIgnoreFilePrefix().stream().anyMatch(file.getName()::startsWith)) {
                        deleteFile(username, file);
                        log.info("删除不存在的文档: {}", file.getAbsolutePath());
                        fileDAO.removeById(fileId);
                    } else {
                        if (fileId != null) {
                            long getModifiedCount = fileDAO.unsetDelTag(fileId);
                            if (getModifiedCount < 1) {
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

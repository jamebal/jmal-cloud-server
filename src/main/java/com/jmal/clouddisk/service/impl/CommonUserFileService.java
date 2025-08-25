package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.lucene.EtagService;
import com.jmal.clouddisk.lucene.LuceneIndexQueueEvent;
import com.jmal.clouddisk.lucene.RebuildIndexTaskService;
import com.jmal.clouddisk.media.*;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.Music;
import com.jmal.clouddisk.model.OperationPermission;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.oss.OssConfigService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.*;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.result.UpdateResult;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.jmal.clouddisk.service.Constants.UPDATE_DATE;
import static com.jmal.clouddisk.service.Constants.UPLOAD_DATE;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommonUserFileService {

    /**
     * 上传文件夹的写入锁缓存
     */
    private final Cache<String, Lock> uploadFileLockCache = CaffeineUtil.getUploadFileLockCache();

    private final FileProperties fileProperties;

    private final PathService  pathService;

    private final MongoTemplate mongoTemplate;

    private final MessageService messageService;

    private final ApplicationEventPublisher eventPublisher;

    private final EtagService etagService;

    private final CommonUserService commonUserService;

    private final VideoProcessService videoProcessService;

    private final ImageMagickProcessor imageMagickProcessor;

    public FileDocument getFileDocument(String userId, String fileName, String relativePath, Query query) {
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("path").is(relativePath));
        query.addCriteria(Criteria.where("name").is(fileName));
        // 文件是否存在
        return mongoTemplate.findOne(query, FileDocument.class);
    }

    /**
     * 创建文件索引
     * @param username 用户名
     * @param file File
     * @param userId 用户Id
     * @param isPublic 是否为公共文件
     * @return fileId
     */
    public String createFile(String username, File file, String userId, Boolean isPublic) {
        if (CaffeineUtil.hasUploadFileCache(file.getAbsolutePath())) {
            return null;
        }
        if (CharSequenceUtil.isBlank(username)) {
            return null;
        }
        if (CharSequenceUtil.isBlank(userId)) {
            userId = commonUserService.getUserIdByUserName(username);
            if (CharSequenceUtil.isBlank(userId)) {
                return null;
            }
        }
        String fileName = file.getName();
        String suffix = MyFileUtils.extName(fileName);
        String contentType = CommonFileService.getContentType(file, FileContentTypeUtils.getContentType(file, suffix));
        if (contentType.startsWith(Constants.CONTENT_TYPE_IMAGE)) {
            // 换成webp格式的图片
            file = replaceWebp(userId, file, username);
            if (file == null) {
                return null;
            }
        }

        String fileAbsolutePath = file.getAbsolutePath();
        Lock lock = uploadFileLockCache.get(fileAbsolutePath, key -> new ReentrantLock());
        if (lock != null) {
            lock.lock();
        }
        UpdateResult updateResult = null;
        ObjectId objectId = new ObjectId();
        String fileId = objectId.toHexString();
        try {
            String relativePath = pathService.getRelativePath(username, fileAbsolutePath, fileName);
            if (relativePath == null) return null;
            Query query = new Query();
            FileDocument fileExists = getFileDocument(userId, fileName, relativePath, query);
            if (fileExists != null) {
                // 添加文件索引
                // 获取tagName
                Update update = new Update();
                updateExifInfo(file, fileExists, contentType, suffix, update);
                updateVideoInfo(file, fileExists, contentType, update);
                updateOtherInfo(fileExists, contentType, suffix, update);
                updateLastModifiedTime(file, fileExists, update);
                if (!update.getUpdateObject().isEmpty()) {
                    mongoTemplate.updateFirst(query, update, FileDocument.class);
                }
                eventPublisher.publishEvent(new LuceneIndexQueueEvent(this, fileExists.getId()));
                return fileExists.getId();
            }
            Update update = new Update();
            // 设置创建时间和修改时间
            update.set(UPLOAD_DATE, LocalDateTime.now(TimeUntils.ZONE_ID));
            update.set(UPDATE_DATE, getFileLastModifiedTime(file));
            update.set("_id", objectId);
            update.set(IUserService.USER_ID, userId);
            update.set("name", fileName);
            update.set("path", relativePath);
            update.set(Constants.IS_FOLDER, file.isDirectory());
            update.set(Constants.IS_FAVORITE, false);
            if (isPublic != null) {
                update.set("isPublic", true);
            }
            if (file.isFile()) {
                setFileConfig(fileId, username, file, fileName, suffix, contentType, relativePath, update);
            } else {
                // 检查目录是否为OSS目录
                checkOSSPath(username, relativePath, fileName, update);
            }
            // 检查该文件的上级目录是否有已经分享的目录
            checkShareBase(update, relativePath);
            updateResult = mongoTemplate.upsert(query, update, FileDocument.class);
            messageService.pushMessage(username, update.getUpdateObject(), Constants.CREATE_FILE);
            // 添加文件索引
            eventPublisher.publishEvent(new LuceneIndexQueueEvent(this, fileId));
            if (file.isDirectory()) {
                etagService.handleNewFolderCreationAsync(username, file);
            }
        } catch (Exception e) {
            log.error("{} file: {}", e.getMessage(), file.getAbsoluteFile(), e);
        } finally {
            if (lock != null) {
                lock.unlock();
                uploadFileLockCache.invalidate(fileAbsolutePath);
            }
        }
        if (updateResult != null && null != updateResult.getUpsertedId()) {
            return updateResult.getUpsertedId().asObjectId().getValue().toHexString();
        }
        return fileId;
    }

    @Nullable
    public Document getShareBaseDocument(String relativePath) {
        if (CharSequenceUtil.isBlank(relativePath)) {
            return null;
        }
        Path path = Paths.get(relativePath);
        StringBuilder pathStr = new StringBuilder("/");
        List<Document> documentList = new ArrayList<>(path.getNameCount());
        for (int i = 0; i < path.getNameCount(); i++) {
            String filename = path.getName(i).toString();
            if (i > 0) {
                pathStr.append("/");
            }
            Document document = new Document("path", pathStr.toString()).append("name", filename);
            documentList.add(document);
            pathStr.append(filename);
        }
        if (documentList.isEmpty()) {
            return null;
        }
        List<Document> list = Arrays.asList(new Document("$match", new Document("$or", documentList)), new Document("$match", new Document(Constants.SHARE_BASE, true)));
        AggregateIterable<Document> result = mongoTemplate.getCollection(CommonFileService.COLLECTION_NAME).aggregate(list);
        Document shareDocument = null;
        try (MongoCursor<Document> mongoCursor = result.iterator()) {
            while (mongoCursor.hasNext()) {
                shareDocument = mongoCursor.next();
            }
        }
        return shareDocument;
    }

    public void checkShareBase(Update update, String relativePath) {
        Document shareDocument = getShareBaseDocument(relativePath);
        if (shareDocument == null) {
            return;
        }
        Long expiresAt = Convert.toLong(shareDocument.get(Constants.EXPIRES_AT), null);
        if (expiresAt == null) {
            return;
        }
        String shareId = Convert.toStr(shareDocument.get(Constants.SHARE_ID), null);
        if (shareId == null) {
            return;
        }
        Boolean isPrivacy = Convert.toBool(shareDocument.get(Constants.IS_PRIVACY), null);
        if (isPrivacy == null) {
            return;
        }
        String extractionCode = Convert.toStr(shareDocument.get(Constants.EXTRACTION_CODE), null);
        if (isPrivacy && extractionCode == null) {
            return;
        }
        List<OperationPermission> operationPermissionList = new ArrayList<>();
        if (shareDocument.get(Constants.OPERATION_PERMISSION_LIST) != null) {
            operationPermissionList = Convert.toList(OperationPermission.class, shareDocument.get(Constants.OPERATION_PERMISSION_LIST));
        }
        CommonFileService.setShareAttribute(update, expiresAt, shareId, isPrivacy, extractionCode, operationPermissionList);
    }

    /**
     * 检查目录是否为OSS目录
     *
     * @param username     username
     * @param relativePath relativePath
     * @param fileName     fileName
     * @param update       Update
     */
    private static void checkOSSPath(String username, String relativePath, String fileName, Update update) {
        if (!MyWebdavServlet.PATH_DELIMITER.equals(relativePath)) {
            return;
        }
        Path prePath = Paths.get(username, relativePath, fileName);
        String ossPath = CaffeineUtil.getOssPath(prePath);
        if (ossPath != null) {
            update.set("ossFolder", CaffeineUtil.getOssDiameterPrefixCache(ossPath).getFolderName());
            update.set("ossPlatform", OssConfigService.getOssStorageService(ossPath).getPlatform().getValue());
        }
    }

    /**
     * 修改文件的最后修改时间
     *
     * @param file       文件
     * @param fileExists FileDocument
     * @param update     Update
     */
    private void updateLastModifiedTime(File file, FileDocument fileExists, Update update) {
        LocalDateTime lastModifiedTime = getFileLastModifiedTime(file);
        // 判断 fileExists.getUpdateDate() 和 lastModifiedTime是否在1ms内
        if (fileExists.getUpdateDate() != null && lastModifiedTime.isEqual(fileExists.getUpdateDate())) {
            update.set(UPDATE_DATE, lastModifiedTime);
        }
    }

    /**
     * 设置文件的最后修改时间
     *
     * @param filePath     文件路径
     * @param lastModified 最后修改时间
     * @throws IOException IO异常
     */
    public static void setLastModifiedTime(Path filePath, Long lastModified) throws IOException {
        if (lastModified == null) {
            return;
        }
        Instant instant = Instant.ofEpochMilli(lastModified);
        FileTime fileTime = FileTime.from(instant);
        Files.setLastModifiedTime(filePath, fileTime);
    }

    private void updateOtherInfo(FileDocument fileExists, String contentType, String suffix, Update update) {
        if (!contentType.equals(fileExists.getContentType())) {
            update.set(Constants.CONTENT_TYPE, contentType);
        }
        if (CharSequenceUtil.isNotBlank(suffix) && !suffix.equals(fileExists.getSuffix())) {
            update.set(Constants.SUFFIX, suffix);
        }
    }

    /**
     * 更新文档的Exif信息
     *
     * @param file        文件
     * @param fileExists  文件信息
     * @param contentType 文件类型
     * @param suffix      文件后缀
     */
    private void updateExifInfo(File file, FileDocument fileExists, String contentType, String suffix, Update update) {
        if (!ImageExifUtil.isImageType(contentType, suffix)) {
            return;
        }
        if (fileExists.getExif() == null || RebuildIndexTaskService.isSyncFile()) {
            // 更新图片Exif信息
            update.set("exif", ImageExifUtil.getExif(file));
        }
    }

    private void updateVideoInfo(File file, FileDocument fileExists, String contentType, Update update) {
        if (!contentType.contains(Constants.VIDEO)) {
            return;
        }
        if (fileExists.getVideo() == null || RebuildIndexTaskService.isSyncFile()) {
            VideoInfo videoInfo = videoProcessService.getVideoInfo(file);
            VideoInfoDO videoInfoDO = videoInfo.toVideoInfoDO();
            update.set("video.bitrate", videoInfoDO.getBitrate());
            update.set("video.bitrateNum", videoInfoDO.getBitrateNum());
            update.set("video.format", videoInfoDO.getFormat());
            update.set("video.duration", videoInfoDO.getDuration());
            update.set("video.durationNum", videoInfoDO.getDurationNum());
            update.set("video.width", videoInfoDO.getWidth());
            update.set("video.height", videoInfoDO.getHeight());
            update.set("video.frameRate", videoInfoDO.getFrameRate());
        }
    }

    public static LocalDateTime getFileLastModifiedTime(File file) {
        try {
            Map<String, Object> attributes = Files.readAttributes(file.toPath(), "lastModifiedTime,creationTime", LinkOption.NOFOLLOW_LINKS);
            FileTime lastModifiedTime = (FileTime) attributes.get("lastModifiedTime");
            return LocalDateTimeUtil.of(lastModifiedTime.toInstant());
        } catch (IOException e) {
            return LocalDateTime.now(TimeUntils.ZONE_ID);
        }
    }

    private void setFileConfig(String fileId, String username, File file, String fileName, String suffix, String contentType, String relativePath, Update update) {
        try {
            long size = file.length();
            update.set("size", size);
            update.set("md5", size + relativePath + fileName);
            update.set(Constants.CONTENT_TYPE, contentType);
            update.set(Constants.SUFFIX, suffix);
            if (contentType.contains(Constants.AUDIO)) {
                setMusic(file, update);
            }
            if (contentType.contains(Constants.VIDEO)) {
                setMediaCover(fileId, username, fileName, relativePath, update);
            }
            if (ImageExifUtil.isImageType(contentType, suffix)) {
                // 处理图片
                processImage(file, update);
            }
            if (contentType.contains(Constants.CONTENT_TYPE_MARK_DOWN) || "md".equals(suffix)) {
                // 写入markdown内容
                String markDownContent = FileUtil.readString(file, MyFileUtils.getFileCharset(file));
                update.set("contentText", markDownContent);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void setMusic(File file, Update update) {
        Music music = AudioFileUtils.readAudio(file);
        if (update == null) {
            update = new Update();
        }
        update.set("music", music);
    }

    private void setMediaCover(String fileId, String username, String fileName, String relativePath, Update update) {
        VideoInfo videoInfo = videoProcessService.getVideoCover(fileId, username, relativePath, fileName);
        String coverPath = videoInfo.getCovertPath();
        log.debug("\r\ncoverPath:{}", coverPath);
        if (!CharSequenceUtil.isBlank(coverPath)) {
            if (update == null) {
                update = new Update();
            }
            update.set("content", PathUtil.readBytes(Paths.get(coverPath)));
            update.set("video", videoInfo.toVideoInfoDO());
            videoProcessService.convertToM3U8(fileId);
            update.set("mediaCover", true);
            FileUtil.del(coverPath);
        } else {
            update.set("mediaCover", false);
        }
    }

    private void processImage(File file, Update update) {
        // 获取图片尺寸
        ImageMagickProcessor.ImageFormat imageFormat = ImageMagickProcessor.identifyFormat(file);
        if (imageFormat != null) {
            int srcWidth = imageFormat.getWidth();
            int srcHeight = imageFormat.getHeight();
            if (srcWidth > 0 && srcHeight > 0) {
                update.set("w", imageFormat.getWidth());
                update.set("h", imageFormat.getHeight());
            }
        }
        // 获取图片Exif信息
        update.set("exif", ImageExifUtil.getExif(file));
        // 生成缩略图
        imageMagickProcessor.generateThumbnail(file, update);
    }

    private File replaceWebp(String userId, File file, String username) {
        String suffix = FileUtil.getSuffix(file).toLowerCase();

        if (getDisabledWebp(userId) || ("ico".equals(suffix))) {
            return file;
        }
        if (Constants.SUFFIX_WEBP.equals(suffix)) {
            return file;
        }
        // 去掉fileName中的后缀
        String fileNameWithoutSuffix = StrUtil.removeSuffix(file.getName(), "." + FileUtil.getSuffix(file.getName()));
        File outputFile = new File(file.getParentFile().getAbsoluteFile(), fileNameWithoutSuffix + Constants.POINT_SUFFIX_WEBP);
        ImageMagickProcessor.replaceWebp(file, outputFile, true);
        return outputFile;
    }

    /**
     * 如果文件夹不存在，则创建
     * @param docPaths  文件夹path
     * @param username username
     * @param userId userId
     */
    public void upsertFolder(@NotNull Path docPaths, @NotNull String username, @NotNull String userId) {
        File dir = Paths.get(fileProperties.getRootDir(), username, docPaths.toString()).toFile();
        if (!dir.exists()) {
            StringBuilder parentPath = new StringBuilder();
            for (int i = 0; i < docPaths.getNameCount(); i++) {
                String name = docPaths.getName(i).toString();
                UploadApiParamDTO uploadApiParamDTO = new UploadApiParamDTO();
                uploadApiParamDTO.setIsFolder(true);
                uploadApiParamDTO.setFilename(name);
                uploadApiParamDTO.setUsername(username);
                uploadApiParamDTO.setUserId(userId);
                if (i > 0) {
                    uploadApiParamDTO.setCurrentDirectory(parentPath.toString());
                }
                createFolder(uploadApiParamDTO);
                parentPath.append("/").append(name);
            }
        }
    }

    public void createFolder(UploadApiParamDTO upload) {
        // 新建文件夹
        String userDirectoryFilePath = getUserDirectoryFilePath(upload);
        //没有分片,直接存
        File dir = new File(fileProperties.getRootDir() + File.separator + upload.getUsername() + userDirectoryFilePath);
        if (!dir.exists()) {
            FileUtil.mkdir(dir);
        }
        // 保存文件夹信息
        createFile(upload.getUsername(), dir, null, null);
    }

    /***
     * 用户磁盘目录
     * @param upload UploadApiParamDTO
     * @return 目录路径
     */
    public String getUserDirectoryFilePath(UploadApiParamDTO upload) {
        String currentDirectory = upload.getCurrentDirectory();
        if (CharSequenceUtil.isBlank(currentDirectory)) {
            currentDirectory = fileProperties.getSeparator();
        }
        Path path;
        if (Boolean.TRUE.equals(upload.getIsFolder())) {
            path = Paths.get(currentDirectory, upload.getFolderPath(), upload.getFilename());
        } else {
            path = Paths.get(currentDirectory, upload.getRelativePath());
        }
        return path.toString();
    }


    public boolean getDisabledWebp(String userId) {
        boolean result = true;
        ConsumerDO consumer = commonUserService.getUserInfoById(userId);
        if (consumer != null && consumer.getWebpDisabled() != null) {
            result = consumer.getWebpDisabled();
        }
        return result;
    }

    /**
     * 获取文件路径(含用户名)获取文件信息
     *
     * @param filepath 文件的相对路径(以username开头)
     * @param userId   userId
     * @return FileDocument
     */
    public FileDocument getFileDocumentByPath(String filepath, String userId) {
        Path relativePath = Paths.get(filepath);
        String filename = relativePath.getFileName().toString();
        String path = File.separator;
        if (relativePath.getNameCount() > 2) {
            path += relativePath.subpath(1, relativePath.getNameCount() - 1) + File.separator;
        }
        return getFileDocumentByPath(path, filename, userId);
    }

    /**
     * 获取文件信息
     *
     * @param path     文件的相对路径
     * @param filename 文件名称
     * @param userId   userId
     * @return FileDocument
     */
    public FileDocument getFileDocumentByPath(String path, String filename, String userId) {
        return getFileDocumentByPath(path, filename, userId, true);
    }

    /**
     * 获取文件信息
     *
     * @param path     文件的相对路径
     * @param filename 文件名称
     * @param userId   userId
     * @return FileDocument
     */
    public FileDocument getFileDocumentByPath(String path, String filename, String userId, boolean excludeContent) {
        Query query = CommonFileService.getQuery(path, filename, userId);
        if (excludeContent) {
            query.fields().exclude("content");
        }
        query.fields().exclude("contentText");
        return mongoTemplate.findOne(query, FileDocument.class);
    }

}

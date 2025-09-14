package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.dao.DataSourceType;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import com.jmal.clouddisk.lucene.EtagService;
import com.jmal.clouddisk.lucene.LuceneIndexQueueEvent;
import com.jmal.clouddisk.lucene.RebuildIndexTaskService;
import com.jmal.clouddisk.media.ImageMagickProcessor;
import com.jmal.clouddisk.media.VideoInfo;
import com.jmal.clouddisk.media.VideoInfoDO;
import com.jmal.clouddisk.media.VideoProcessService;
import com.jmal.clouddisk.model.Music;
import com.jmal.clouddisk.model.OperationPermission;
import com.jmal.clouddisk.model.ShareBaseInfoDTO;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.dto.UpdateFile;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.oss.OssConfigService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.util.*;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.mongodb.core.query.Query;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
@Slf4j
@RequiredArgsConstructor
public class CommonUserFileService {

    /**
     * 上传文件夹的写入锁缓存
     */
    private final Cache<String, Lock> uploadFileLockCache = CaffeineUtil.getUploadFileLockCache();

    private final FileProperties fileProperties;

    private final IFileDAO fileDAO;

    private final PathService  pathService;

    private final MessageService messageService;

    private final ApplicationEventPublisher eventPublisher;

    private final EtagService etagService;

    private final CommonUserService commonUserService;

    private final VideoProcessService videoProcessService;

    private final ImageMagickProcessor imageMagickProcessor;

    private final DataSourceProperties dataSourceProperties;

    private final FilePersistenceService filePersistenceService;

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
            file = replaceWebp(userId, file);
            if (file == null) {
                return null;
            }
        }

        String fileAbsolutePath = file.getAbsolutePath();
        Lock lock = uploadFileLockCache.get(fileAbsolutePath, _ -> new ReentrantLock());
        if (lock != null) {
            lock.lock();
        }
        String upsertFileId = null;
        ObjectId objectId = new ObjectId();
        String fileId = objectId.toHexString();
        try {
            String relativePath = pathService.getRelativePath(username, fileAbsolutePath, fileName);
            if (relativePath == null) return null;
            FileDocument fileExists = fileDAO.findByUserIdAndPathAndName(userId, relativePath, fileName);
            if (fileExists != null) {
                // 添加文件索引
                // 获取tagName
                UpdateFile updateFile = new UpdateFile();
                updateExifInfo(file, fileExists, contentType, suffix, updateFile);
                updateVideoInfo(file, fileExists, contentType, updateFile);
                updateOtherInfo(fileExists, contentType, suffix, updateFile);
                updateLastModifiedTime(file, fileExists, updateFile);
                if (updateFile.isNotEmpty()) {
                    fileDAO.updateFileByUserIdAndPathAndName(userId, relativePath, fileName, updateFile);
                }
                eventPublisher.publishEvent(new LuceneIndexQueueEvent(this, fileExists.getId()));
                return fileExists.getId();
            }
            FileDocument fileDocument = new FileDocument();
            // 设置创建时间和修改时间
            fileDocument.setUploadDate(LocalDateTime.now(TimeUntils.ZONE_ID));
            fileDocument.setUpdateDate(getFileLastModifiedTime(file));
            fileDocument.setId(fileId);
            fileDocument.setUserId(userId);
            fileDocument.setName(fileName);
            fileDocument.setPath(relativePath);
            fileDocument.setIsFolder(file.isDirectory());
            fileDocument.setIsFavorite(false);
            if (isPublic != null) {
                fileDocument.setIsPublic(true);
            }
            if (file.isFile()) {
                setFileConfig(fileId, username, file, fileName, suffix, contentType, relativePath, fileDocument);
            } else {
                // 检查目录是否为OSS目录
                checkOSSPath(username, relativePath, fileName, fileDocument);
            }
            // 检查该文件的上级目录是否有已经分享的目录
            checkShareBase(fileDocument, relativePath);
            upsertFileId = fileDAO.upsertByUserIdAndPathAndName(userId, relativePath, fileName, fileDocument);
            messageService.pushMessage(username, fileDocument, Constants.CREATE_FILE);
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
        if (upsertFileId != null) {
            return upsertFileId;
        }
        return fileId;
    }

    @Nullable
    public ShareBaseInfoDTO getShareBaseDocument(String relativePath) {
        if (CharSequenceUtil.isBlank(relativePath)) {
            return null;
        }
        return fileDAO.getShareBaseByPath(relativePath);
    }

    public void checkShareBase(FileDocument fileDocument, String relativePath) {
        ShareBaseInfoDTO shareDocument = getShareBaseDocument(relativePath);
        if (shareDocument == null) {
            return;
        }
        Long expiresAt = Convert.toLong(shareDocument.getExpireDate(), null);
        if (expiresAt == null) {
            return;
        }
        String shareId = Convert.toStr(shareDocument.getShareId(), null);
        if (shareId == null) {
            return;
        }
        Boolean isPrivacy = Convert.toBool(shareDocument.getIsPrivacy(), null);
        if (isPrivacy == null) {
            return;
        }
        String extractionCode = Convert.toStr(shareDocument.getExtractionCode(), null);
        if (isPrivacy && extractionCode == null) {
            return;
        }
        List<OperationPermission> operationPermissionList;
        if (shareDocument.getOperationPermissionList() != null) {
            operationPermissionList = Convert.toList(OperationPermission.class, shareDocument.getOperationPermissionList());
            fileDocument.setOperationPermissionList(operationPermissionList);
        }
        fileDocument.setIsShare(true);
        fileDocument.setShareId(shareId);
        fileDocument.setExpiresAt(expiresAt);
        fileDocument.setIsPrivacy(isPrivacy);
        if (BooleanUtil.isTrue(isPrivacy)) {
            fileDocument.setExtractionCode(extractionCode);
        } else {
            fileDocument.setExtractionCode(null);
        }
    }

    /**
     * 检查目录是否为OSS目录
     *
     * @param username     username
     * @param relativePath relativePath
     * @param fileName     fileName
     */
    private static void checkOSSPath(String username, String relativePath, String fileName, FileDocument fileDocument) {
        if (!MyWebdavServlet.PATH_DELIMITER.equals(relativePath)) {
            return;
        }
        Path prePath = Paths.get(username, relativePath, fileName);
        String ossPath = CaffeineUtil.getOssPath(prePath);
        if (ossPath != null) {
            fileDocument.setOssFolder(CaffeineUtil.getOssDiameterPrefixCache(ossPath).getFolderName());
            fileDocument.setOssPlatform(OssConfigService.getOssStorageService(ossPath).getPlatform().getValue());
        }
    }

    /**
     * 修改文件的最后修改时间
     */
    private void updateLastModifiedTime(File file, FileDocument fileExists, UpdateFile updateFile) {
        LocalDateTime lastModifiedTime = getFileLastModifiedTime(file);
        // 判断 fileExists.getUpdateDate() 和 lastModifiedTime是否在1ms内
        if (fileExists.getUpdateDate() != null && lastModifiedTime.isEqual(fileExists.getUpdateDate())) {
            updateFile.setUpdateDate(lastModifiedTime);
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

    private void updateOtherInfo(FileDocument fileExists, String contentType, String suffix, UpdateFile updateFile) {
        if (!contentType.equals(fileExists.getContentType())) {
            updateFile.setContentType(contentType);
        }
        if (CharSequenceUtil.isNotBlank(suffix) && !suffix.equals(fileExists.getSuffix())) {
            updateFile.setSuffix(suffix);
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
    private void updateExifInfo(File file, FileDocument fileExists, String contentType, String suffix, UpdateFile updateFile) {
        if (!ImageExifUtil.isImageType(contentType, suffix)) {
            return;
        }
        if (fileExists.getExif() == null || RebuildIndexTaskService.isSyncFile()) {
            // 更新图片Exif信息
            updateFile.setExif(ImageExifUtil.getExif(file));
        }
    }

    private void updateVideoInfo(File file, FileDocument fileExists, String contentType, UpdateFile updateFile) {
        if (!contentType.contains(Constants.VIDEO)) {
            return;
        }
        if (fileExists.getVideo() == null || RebuildIndexTaskService.isSyncFile()) {
            VideoInfo videoInfo = videoProcessService.getVideoInfo(file);
            VideoInfoDO videoInfoDO = videoInfo.toVideoInfoDO();
            updateFile.setVideo(videoInfoDO);
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

    private void setFileConfig(String fileId, String username, File file, String fileName, String suffix, String contentType, String relativePath, FileDocument fileDocument) {
        try {
            long size = file.length();
            fileDocument.setSize(size);
            fileDocument.setMd5(size + relativePath + fileName);
            fileDocument.setContentType(contentType);
            fileDocument.setSuffix(suffix);
            if (contentType.contains(Constants.AUDIO)) {
                setMusic(file, fileDocument);
            }
            if (contentType.contains(Constants.VIDEO)) {
                setMediaCover(fileId, username, fileName, relativePath, fileDocument);
            }
            if (ImageExifUtil.isImageType(contentType, suffix)) {
                // 处理图片
                processImage(file, fileDocument);
            }
            if (contentType.contains(Constants.CONTENT_TYPE_MARK_DOWN) || "md".equals(suffix)) {
                // 写入markdown内容
                String markDownContent = FileUtil.readString(file, MyFileUtils.getFileCharset(file));

                if (dataSourceProperties.getType() == DataSourceType.mongodb) {
                    fileDocument.setContentText(markDownContent);
                } else {
                    filePersistenceService.persistContent(fileDocument.getId(), file.toPath());
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private static void setMusic(File file, FileDocument fileDocument) {
        Music music = AudioFileUtils.readAudio(file);
        fileDocument.setMusic(music);
    }

    private void setMediaCover(String fileId, String username, String fileName, String relativePath, FileDocument fileDocument) {
        VideoInfo videoInfo = videoProcessService.getVideoCover(fileId, username, relativePath, fileName);
        String coverPath = videoInfo.getCovertPath();
        log.debug("\r\ncoverPath:{}", coverPath);
        if (!CharSequenceUtil.isBlank(coverPath)) {
            Path contentPath = Paths.get(coverPath);

            if (dataSourceProperties.getType() == DataSourceType.mongodb) {
                fileDocument.setContent(PathUtil.readBytes(contentPath));
            } else {
               filePersistenceService.persistContent(fileDocument.getId(), contentPath);
            }
            fileDocument.setVideo(videoInfo.toVideoInfoDO());
            videoProcessService.convertToM3U8(fileId);
            fileDocument.setMediaCover(true);
            FileUtil.del(coverPath);
        } else {
            fileDocument.setMediaCover(false);
        }
    }

    private void processImage(File file, FileDocument fileDocument) {
        // 获取图片尺寸
        ImageMagickProcessor.ImageFormat imageFormat = ImageMagickProcessor.identifyFormat(file);
        if (imageFormat != null) {
            int srcWidth = imageFormat.getWidth();
            int srcHeight = imageFormat.getHeight();
            if (srcWidth > 0 && srcHeight > 0) {
                fileDocument.setW(Convert.toStr(srcWidth));
                fileDocument.setH(Convert.toStr(srcHeight));
            }
        }
        // 获取图片Exif信息
        fileDocument.setExif(ImageExifUtil.getExif(file));
        // 生成缩略图
        imageMagickProcessor.generateThumbnail(file, fileDocument);
    }

    private File replaceWebp(String userId, File file) {
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
        Query query = CommonFileService.getQuery(userId, path, filename);
        if (excludeContent) {
            query.fields().exclude(Constants.CONTENT);
        }
        query.fields().exclude(Constants.CONTENT_TEXT);
        if (excludeContent) {
            return fileDAO.findByUserIdAndPathAndName(userId, path, filename, Constants.CONTENT, Constants.CONTENT_DRAFT, Constants.CONTENT_DRAFT, Constants.CONTENT_HTML);
        } else {
            return fileDAO.findByUserIdAndPathAndName(userId, path, filename, Constants.CONTENT_DRAFT, Constants.CONTENT_DRAFT, Constants.CONTENT_HTML);
        }
    }

}

package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSONObject;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.controller.sse.Message;
import com.jmal.clouddisk.controller.sse.SseController;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.lucene.LuceneService;
import com.jmal.clouddisk.lucene.RebuildIndexTaskService;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.oss.OssConfigService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.*;
import com.jmal.clouddisk.video.VideoInfo;
import com.jmal.clouddisk.video.VideoInfoDO;
import com.jmal.clouddisk.video.VideoProcessService;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import com.luciad.imageio.webp.WebPWriteParam;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.tasks.UnsupportedFormatException;
import org.bson.BsonNull;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.jetbrains.annotations.Nullable;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.Collator;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.jmal.clouddisk.service.Constants.UPDATE_DATE;
import static com.jmal.clouddisk.service.Constants.UPLOAD_DATE;
import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.eq;

/**
 * @author jmal
 * @Description CommonFileService
 * @date 2023/4/7 17:27
 */
@Service
@Slf4j
public class CommonFileService {

    @Autowired
    UserLoginHolder userLoginHolder;

    @Autowired
    IUserService userService;

    public static final String COLLECTION_NAME = "fileDocument";

    public static final String TRASH_COLLECTION_NAME = "trash";

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    FileProperties fileProperties;

    @Autowired
    private SseController sseController;

    @Autowired
    private VideoProcessService videoProcessService;

    @Autowired
    public LuceneService luceneService;

    private final Cache<String, Map<String, ThrottleExecutor>> throttleExecutorCache = Caffeine.newBuilder().build();

    /**
     * 上传文件夹的写入锁缓存
     */
    private final Cache<String, Lock> uploadFileLockCache = CaffeineUtil.getUploadFileLockCache();

    protected static final Set<String> FILE_PATH_LOCK = new CopyOnWriteArraySet<>();

    public ResponseEntity<Object> getObjectResponseEntity(FileDocument fileDocument) {
        if (fileDocument != null) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "fileName=" + UriUtils.encode(fileDocument.getName(), StandardCharsets.UTF_8))
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

    /**
     * 是否存在该文件
     * @param path 文件的相对路径
     * @param userId userId 用户Id
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
     * @param path 文件的相对路径
     * @param userId userId
     * @param md5 md5
     * @return FileDocument
     */
    FileDocument getByMd5(String path, String userId, String md5) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("md5").is(md5));
        query.addCriteria(Criteria.where("path").is(path));
        return mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
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
        Query query = getQuery(path, filename, userId);
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

    /***
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
     * 创建文件索引
     * @param username 用户名
     * @param file File
     * @param userId 用户Id
     * @param isPublic 是否为公共文件
     * @return fileId
     */
    public String createFile(String username, File file, String userId, Boolean isPublic) {
        if (CharSequenceUtil.isBlank(username)) {
            return null;
        }
        if (CharSequenceUtil.isBlank(userId)) {
            userId = userService.getUserIdByUserName(username);
            if (CharSequenceUtil.isBlank(userId)) {
                return null;
            }
        }
        String fileName = file.getName();
        String suffix = FileUtil.extName(fileName);
        String contentType = getContentType(file, FileContentTypeUtils.getContentType(suffix));
        if (contentType.startsWith(Constants.CONTENT_TYPE_IMAGE)) {
            // 换成webp格式的图片
            file = replaceWebp(userId, file);
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
            String relativePath = getRelativePath(username, fileAbsolutePath, fileName);
            if (relativePath == null) return null;
            Query query = new Query();
            FileDocument fileExists = getFileDocument(userId, fileName, relativePath, query);
            if (fileExists != null) {
                // 添加文件索引
                // 获取tagName
                Update update = new Update();
                updateExifInfo(file, fileExists, contentType, suffix, update);
                updateVideoInfo(file, fileExists, contentType, update);
                updateOtherInfo(fileExists, contentType, update);
                if (!update.getUpdateObject().isEmpty()) {
                    mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
                }
                luceneService.pushCreateIndexQueue(fileExists.getId());
                return fileExists.getId();
            }
            Update update = new Update();
            // 设置创建时间和修改时间
            setDateTime(file, update);
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
            updateResult = mongoTemplate.upsert(query, update, COLLECTION_NAME);
            pushMessage(username, update.getUpdateObject(), Constants.CREATE_FILE);
            // 添加文件索引
            luceneService.pushCreateIndexQueue(fileId);
            // 判断回收站是否存在该文件, 如果存在则删除
            checkTrash(file, username);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
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

    private void checkTrash(File file, String username) {
        String userId = userService.getUserIdByUserName(username);
        String relativePath = getRelativePath(username, String.valueOf(file.getAbsoluteFile()), file.getName());
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("path").is(relativePath));
        query.addCriteria(Criteria.where("name").is(file.getName()));
        mongoTemplate.remove(query, TRASH_COLLECTION_NAME);
    }

    private void updateOtherInfo(FileDocument fileExists, String contentType, Update update) {
        if (!contentType.equals(fileExists.getContentType())) {
            update.set(Constants.CONTENT_TYPE, contentType);
        }
    }

    private String getRelativePath(String username, String fileAbsolutePath, String fileName) {
        int startIndex = fileProperties.getRootDir().length() + username.length() + 1;
        int endIndex = fileAbsolutePath.length() - fileName.length();
        if (startIndex >= endIndex) {
            return null;
        }
        return fileAbsolutePath.substring(startIndex, endIndex);
    }

    /**
     * 更新文档的Exif信息
     * @param file 文件
     * @param fileExists 文件信息
     * @param contentType 文件类型
     * @param suffix 文件后缀
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

    private static void setDateTime(File file, Update update) {
        LocalDateTime updateDateTime;
        LocalDateTime uploadDateTime;
        try {
            Map<String, Object> attributes = Files.readAttributes(file.toPath(), "lastModifiedTime,creationTime", LinkOption.NOFOLLOW_LINKS);
            FileTime lastModifiedTime = (FileTime) attributes.get("lastModifiedTime");
            FileTime creationTime = (FileTime) attributes.get("creationTime");
            uploadDateTime = LocalDateTimeUtil.of(creationTime.toInstant());
            updateDateTime = LocalDateTimeUtil.of(lastModifiedTime.toInstant());
        } catch (IOException e) {
            uploadDateTime = LocalDateTime.now(TimeUntils.ZONE_ID);
            updateDateTime = uploadDateTime;
        }
        update.set(UPLOAD_DATE, uploadDateTime);
        update.set(UPDATE_DATE, updateDateTime);
    }

    public FileDocument getFileDocument(String userId, String fileName, String relativePath, Query query) {
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("path").is(relativePath));
        query.addCriteria(Criteria.where("name").is(fileName));
        // 文件是否存在
        return mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
    }

    public FileDocument getFileDocument(String username, String fileAbsolutePath)  {
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
        return getFileDocument(userId, fileName, relativePath, new Query());
    }

    public FileDocument getFileDocument(String userId, String fileName, String relativePath) {
        Query query = new Query();
        // 文件是否存在
        return getFileDocument(userId, fileName, relativePath, query);
    }

    private File replaceWebp(String userId, File file) {
        if (userService.getDisabledWebp(userId) || ("ico".equals(FileUtil.getSuffix(file)))) {
            return file;
        }
        if (Constants.SUFFIX_WEBP.equals(FileUtil.getSuffix(file))) {
            return file;
        }
        File outputFile = new File(file.getPath() + Constants.POINT_SUFFIX_WEBP);
        // 从某处获取图像进行编码
        BufferedImage image;
        try {
            image = ImageIO.read(file);
            if (image == null) {
                return file;
            }
            imageFileToWebp(outputFile, image);
            FileUtil.del(file);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return file;
        }
        return outputFile;
    }

    public void imageFileToWebp(File outputFile, BufferedImage image) throws IOException {
        // 获取一个WebP ImageWriter实例
        ImageWriter writer = ImageIO.getImageWritersByMIMEType(Constants.CONTENT_TYPE_WEBP).next();
        // 配置编码参数
        WebPWriteParam writeParam = new WebPWriteParam(writer.getLocale());
        writeParam.setCompressionMode(ImageWriteParam.MODE_DEFAULT);
        // 在ImageWriter上配置输出
        writer.setOutput(new FileImageOutputStream(outputFile));
        // 编码
        writer.write(null, new IIOImage(image, null, null), writeParam);
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

    private void processImage(File file, Update update) {
        // 获取图片尺寸
        FastImageInfo imageInfo = new FastImageInfo(file);
        if (imageInfo.getWidth() > 0 && imageInfo.getHeight() > 0) {
            update.set("w", imageInfo.getWidth());
            update.set("h", imageInfo.getHeight());
        }
        // 获取图片Exif信息
        update.set("exif", ImageExifUtil.getExif(file));
        // 生成缩略图
        generateThumbnail(file, update);
    }

    public static String getContentType(File file, String contentType) {
        try {
            if (MyFileUtils.hasCharset(file)) {
                String charset = UniversalDetector.detectCharset(file);
                if (StrUtil.isNotBlank(charset) && StandardCharsets.UTF_8.name().equals(charset)) {
                    contentType = contentType + ";charset=utf-8";
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return contentType;
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

    /**
     * 生成缩略图
     * @param file File
     * @param update org.springframework.data.mongodb.core.query.UpdateDefinition
     */
    private void generateThumbnail(File file, Update update) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Thumbnails.Builder<? extends File> thumbnail = Thumbnails.of(file);
            thumbnail.size(256, 256);
            thumbnail.toOutputStream(out);
            update.set("content", out.toByteArray());
        } catch (UnsupportedFormatException e) {
            log.warn("{}{}", e.getMessage(), file.getAbsolutePath());
        } catch (Exception e) {
            log.error("{}{}", e.getMessage(), file.getAbsolutePath());
        } catch (Throwable e) {
            log.error(e.getMessage());
        }
    }

    public void pushMessage(String username, Object message, String url) {
        Completable.fromAction(() -> pushMessageSync(username, message, url))
                .subscribeOn(Schedulers.io())
                .subscribe();
    }

    /**
     * 给用户推送消息
     * @param username username
     * @param message message
     * @param url url
     */
    public void pushMessageSync(String username, Object message, String url) {
        if (timelyPush(username, message, url)) return;
        if (Constants.CREATE_FILE.equals(url) || Constants.DELETE_FILE.equals(url)) {
            Map<String, ThrottleExecutor> throttleExecutorMap = throttleExecutorCache.get(username, key -> new HashMap<>(8));
            if (throttleExecutorMap != null) {
                ThrottleExecutor throttleExecutor = throttleExecutorMap.get(url);
                if (throttleExecutor == null) {
                    throttleExecutor = new ThrottleExecutor(300);
                    throttleExecutorMap.put(url, throttleExecutor);
                }
                throttleExecutor.schedule(() -> pushMsg(username, message, url));
            }
        } else {
            pushMsg(username, message, url);
        }
    }

    private boolean timelyPush(String username, Object message, String url) {
        if (Constants.CREATE_FILE.equals(url)) {
            if (message instanceof Document setDoc) {
                Object set = setDoc.get("$set");
                if (set instanceof Document doc) {
                    doc.remove("content");
                    Boolean isFolder = doc.getBoolean(Constants.IS_FOLDER);
                    if (Boolean.TRUE.equals(isFolder)) {
                        pushMsg(username, message, url);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void pushMsg(String username, Object message, String url) {
        Message msg = new Message();
        String userId = userLoginHolder.getUserId();
        if (CharSequenceUtil.isBlank(userId)) {
            userId = userService.getUserIdByUserName(username);
        }
        if (!CharSequenceUtil.isBlank(userId)) {
            long takeUpSpace = occupiedSpace(userId);
            msg.setSpace(takeUpSpace);
        }
        if (message == null) {
            message = new Document();
        }
        if (message instanceof FileDocument fileDocument) {
            fileDocument.setContent(null);
        }
        msg.setUrl(url);
        msg.setUsername(username);
        msg.setBody(message);
        sseController.sendEvent(msg);
    }

    public void pushMessageOperationFileError(String username, String message, String operation) {
        JSONObject msg = new JSONObject();
        msg.put("code", -1);
        msg.put("msg", message);
        msg.put("operation", operation);
        pushMessage(username, msg, Constants.OPERATION_FILE);
    }

    public void pushMessageOperationFileSuccess(String fromPath, String toPath, String username, String operation) {
        JSONObject msg = new JSONObject();
        msg.put("code", 0);
        msg.put("from", fromPath);
        msg.put("to", toPath);
        msg.put("operation", operation);
        pushMessage(username, msg, Constants.OPERATION_FILE);
    }

    public long occupiedSpace(String userId) {
        long space = calculateTotalOccupiedSpace(userId).blockingGet();
        ConsumerDO consumerDO = userService.userInfoById(userId);
        if (consumerDO != null && consumerDO.getQuota() != null) {
            if (space >= consumerDO.getQuota() * 1024L * 1024L * 1024L) {
                // 空间已满
                CaffeineUtil.setSpaceFull(userId);
            } else {
                if (CaffeineUtil.spaceFull(userId)) {
                    CaffeineUtil.removeSpaceFull(userId);
                }
            }
        }
        return space;
    }

    public Single<Long> calculateTotalOccupiedSpace(String userId) {
        Single<Long> space1Single = getOccupiedSpaceAsync(userId, COLLECTION_NAME);
        Single<Long> space2Single = getOccupiedSpaceAsync(userId, TRASH_COLLECTION_NAME);
        return Single.zip(space1Single, space2Single, Long::sum);
    }

    public Single<Long> getOccupiedSpaceAsync(String userId, String collectionName) {
        return Single.fromCallable(() -> getOccupiedSpace(userId, collectionName))
                .subscribeOn(Schedulers.io());
    }

    private long getOccupiedSpace(String userId, String collectionName) {
        long space = 0;
        List<Bson> list = Arrays.asList(
                match(eq(IUserService.USER_ID, userId)),
                group(new BsonNull(), sum(Constants.TOTAL_SIZE, "$size")));
        AggregateIterable<Document> aggregateIterable = mongoTemplate.getCollection(collectionName).aggregate(list);
        Document doc = aggregateIterable.first();
        if (doc != null) {
            space = Convert.toLong(doc.get(Constants.TOTAL_SIZE), 0L);
        }
        return space;
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
        setShareAttribute(update, expiresAt, shareId, isPrivacy, extractionCode, operationPermissionList);
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
        AggregateIterable<Document> result = mongoTemplate.getCollection(COLLECTION_NAME).aggregate(list);
        Document shareDocument = null;
        try (MongoCursor<Document> mongoCursor = result.iterator()) {
            while (mongoCursor.hasNext()) {
                shareDocument = mongoCursor.next();
            }
        }
        return shareDocument;
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
    private static void setShareAttribute(Update update, long expiresAt, String shareId, Boolean isPrivacy, String extractionCode, List<OperationPermission> operationPermissionListList) {
        update.set(Constants.IS_SHARE, true);
        update.set(Constants.SHARE_ID, shareId);
        update.set(Constants.EXPIRES_AT, expiresAt);
        update.set(Constants.IS_PRIVACY, isPrivacy);
        if (operationPermissionListList != null) {
            update.set(Constants.OPERATION_PERMISSION_LIST, operationPermissionListList);
        }
        if (BooleanUtil.isTrue(isPrivacy)) {
            update.set(Constants.EXTRACTION_CODE, extractionCode);
        }
    }

    /**
     * 通过文件的绝对路径获取用户名
     * @param absolutePath 绝对路径
     * @return 用户名
     */
    public String getUsernameByAbsolutePath(Path absolutePath) {
        if (absolutePath == null) {
            return null;
        }
        Path parentPath = Paths.get(fileProperties.getRootDir());
        if (absolutePath.startsWith(parentPath)) {
            Path relativePath = parentPath.relativize(absolutePath);
            if (relativePath.getNameCount() > 1) {
                return relativePath.getName(0).toString();
            }
        }
        return null;
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

    public void modifyFile(String username, File file) {
        String fileAbsolutePath = file.getAbsolutePath();
        String fileName = file.getName();
        String relativePath = fileAbsolutePath.substring(fileProperties.getRootDir().length() + username.length() + 1, fileAbsolutePath.length() - fileName.length());
        String userId = userService.getUserIdByUserName(username);
        if (CharSequenceUtil.isBlank(userId)) {
            return;
        }
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("path").is(relativePath));
        query.addCriteria(Criteria.where("name").is(fileName));

        String suffix = FileUtil.extName(fileName);
        String contentType = FileContentTypeUtils.getContentType(suffix);
        // 文件是否存在
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            Update update = new Update();
            update.set("size", file.length());
            update.set(Constants.SUFFIX, suffix);
            update.set(Constants.CONTENT_TYPE, getContentType(file, contentType));
            LocalDateTime updateDate = LocalDateTime.now(TimeUntils.ZONE_ID);
            update.set("updateDate", updateDate);
            UpdateResult updateResult = mongoTemplate.upsert(query, update, COLLECTION_NAME);
            fileDocument.setSize(file.length());
            fileDocument.setUpdateDate(updateDate);
            if (contentType.contains(Constants.CONTENT_TYPE_MARK_DOWN) || "md".equals(suffix)) {
                // 写入markdown内容
                String markDownContent = FileUtil.readString(file, MyFileUtils.getFileCharset(file));
                update.set("contentText", markDownContent);
            }
            pushMessage(username, fileDocument, Constants.UPDATE_FILE);
            if (updateResult.getModifiedCount() > 0) {
                luceneService.pushCreateIndexQueue(fileDocument.getId());
            }
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
        if (Boolean.TRUE.equals(f1.getIsFolder()) && Boolean.TRUE.equals(!f2.getIsFolder())) {
            return -1;
        } else if (f1.getIsFolder() && f2.getIsFolder()) {
            return compareByName(f1, f2);
        } else if (Boolean.TRUE.equals(!f1.getIsFolder()) && Boolean.TRUE.equals(f2.getIsFolder())) {
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
        return MyWebdavServlet.PATH_DELIMITER + relativePath + (Boolean.TRUE.equals(file.isDirectory()) ? MyWebdavServlet.PATH_DELIMITER : "");
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
        Query query = new Query();
        query.addCriteria(Criteria.where("delete").is(1));
        DeleteResult deleteResult = mongoTemplate.remove(query, COLLECTION_NAME);
        if (deleteResult.getDeletedCount() > 0) {
            log.info("删除有删除标记的文档: {}", deleteResult.getDeletedCount());
        }
    }
}

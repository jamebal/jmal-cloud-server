package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.CharsetDetector;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.alibaba.fastjson2.JSONObject;
import com.github.benmanes.caffeine.cache.Cache;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.oss.OssConfigService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.video.VideoProcessService;
import com.jmal.clouddisk.util.*;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import com.jmal.clouddisk.websocket.SocketManager;
import com.luciad.imageio.webp.WebPWriteParam;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.tasks.UnsupportedFormatException;
import org.bson.BsonNull;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.util.UriUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import javax.validation.constraints.NotNull;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
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

    @Autowired
    MongoTemplate mongoTemplate;

    @Autowired
    FileProperties fileProperties;

    @Autowired
    private SimpMessagingTemplate template;

    @Autowired
    private VideoProcessService videoProcessService;

    /***
     * 上传文件夹的写入锁缓存
     */
    private final Cache<String, Lock> uploadFolderLockCache = CaffeineUtil.getUploadFolderLockCache();

    protected static final Set<String> FILE_PATH_LOCK = new CopyOnWriteArraySet<>();

    public ResponseEntity<Object> getObjectResponseEntity(Optional<FileDocument> file) {
        return file.<ResponseEntity<Object>>map(fileDocument ->
                ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "fileName=" + ContentDisposition.builder("attachment")
                                .filename(UriUtils.encode(fileDocument.getName(), StandardCharsets.UTF_8)))
                        .header(HttpHeaders.CONTENT_TYPE, fileDocument.getContentType())
                        .header(HttpHeaders.CONNECTION, "close")
                        .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(fileDocument.getContent() != null ? fileDocument.getContent().length : 0))
                        .header(HttpHeaders.CONTENT_ENCODING, "utf-8")
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=604800")
                        .body(fileDocument.getContent())).orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body("找不到该文件"));
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
        if (Boolean.TRUE.equals(upload.getIsFolder())) {
            if (upload.getFolderPath() != null) {
                currentDirectory += fileProperties.getSeparator() + upload.getFolderPath();
            } else {
                currentDirectory += fileProperties.getSeparator() + upload.getFilename();
            }
        } else {
            currentDirectory += fileProperties.getSeparator() + upload.getRelativePath();
        }
        return currentDirectory;
    }

    /***
     * 是否存在该文件
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
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("name").is(filename));
        query.addCriteria(Criteria.where("path").is(path));
        return mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
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
        String path = File.separator + relativePath.subpath(1, relativePath.getNameCount() - 1) + File.separator;
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
        String contentType = FileContentTypeUtils.getContentType(suffix);
        if (contentType.startsWith(Constants.CONTENT_TYPE_IMAGE)) {
            // 换成webp格式的图片
            file = replaceWebp(userId, file);
        }
        fileName = file.getName();
        suffix = FileUtil.extName(fileName);
        contentType = FileContentTypeUtils.getContentType(suffix);

        String fileAbsolutePath = file.getAbsolutePath();
        Lock lock = null;
        if (file.isDirectory()) {
            lock = uploadFolderLockCache.get(fileAbsolutePath, key -> new ReentrantLock());
            if (lock != null) {
                lock.lock();
            }
        }
        UpdateResult updateResult;
        try {
            int startIndex = fileProperties.getRootDir().length() + username.length() + 1;
            int endIndex = fileAbsolutePath.length() - fileName.length();
            if (startIndex >= endIndex) {
                return null;
            }
            String relativePath = fileAbsolutePath.substring(startIndex, endIndex);
            Query query = new Query();
            FileDocument fileExists = getFileDocument(userId, fileName, relativePath, query);
            if (fileExists != null) {
                updateMediaProp(username, file, contentType, query, fileExists);
                return fileExists.getId();
            }
            Update update = new Update();
            // 设置创建时间和修改时间
            setDateTime(file, update);
            update.set(IUserService.USER_ID, userId);
            update.set("name", fileName);
            update.set("path", relativePath);
            update.set(Constants.IS_FOLDER, file.isDirectory());
            update.set(Constants.IS_FAVORITE, false);
            if (isPublic != null) {
                update.set("isPublic", true);
            }
            if (file.isFile()) {
                setFileConfig(username, file, fileName, suffix, contentType, relativePath, update);
            } else {
                // 检查目录是否为OSS目录
                checkOSSPath(username, relativePath, fileName, update);
            }
            // 检查该文件的上级目录是否有已经分享的目录
            checkShareBase(update, relativePath);
            updateResult = mongoTemplate.upsert(query, update, COLLECTION_NAME);
            pushMessage(username, update.getUpdateObject(), "createFile");
        } finally {
            if (lock != null) {
                lock.unlock();
            }
        }
        if (null != updateResult.getUpsertedId()) {
            return updateResult.getUpsertedId().asObjectId().getValue().toHexString();
        }
        return null;
    }

    private void updateMediaProp(String username, File file, String contentType, Query query, FileDocument fileExists) {
        try {
            Update update = null;
            if (contentType.contains(Constants.VIDEO)) {
                setMediaCover(username, fileExists.getName(), fileExists.getPath(), update);
            }
            if (contentType.contains(Constants.AUDIO) && fileExists.getMusic() == null) {
                setMusic(file, update);
            }
            if (update != null) {
                mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
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
        update.set("uploadDate", uploadDateTime);
        update.set("updateDate", updateDateTime);
    }

    public FileDocument getFileDocument(String userId, String fileName, String relativePath, Query query) {
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("path").is(relativePath));
        query.addCriteria(Criteria.where("name").is(fileName));
        // 文件是否存在
        return mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
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

    private void setFileConfig(String username, File file, String fileName, String suffix, String contentType, String relativePath, Update update) {
        long size = file.length();
        update.set("size", size);
        update.set("md5", size + relativePath + fileName);
        update.set(Constants.CONTENT_TYPE, getContentType(file, contentType));
        update.set(Constants.SUFFIX, suffix);
        if (contentType.contains(Constants.AUDIO)) {
            setMusic(file, update);
        }
        if (contentType.contains(Constants.VIDEO)) {
            setMediaCover(username, fileName, relativePath, update);
        }
        if (contentType.startsWith(Constants.CONTENT_TYPE_IMAGE) && (!"ico".equals(suffix) && !"svg".equals(suffix))) {
            generateThumbnail(file, update);
        }
        if (contentType.contains(Constants.CONTENT_TYPE_MARK_DOWN) || "md".equals(suffix)) {
            // 写入markdown内容
            String markDownContent = FileUtil.readString(file, MyFileUtils.getFileCharset(file));
            update.set("contentText", markDownContent);
        }
    }

    public static String getContentType(File file, String contentType) {
        try {
            if (file == null) {
                return contentType;
            }
            if (file.isDirectory()) {
                return contentType;
            }
            if (contentType.contains(Constants.CONTENT_TYPE_MARK_DOWN)) {
                return contentType;
            }
            Charset charset = CharsetDetector.detect(file, null);
            if (charset != null && "UTF-8".equals(charset.toString())) {
                contentType = contentType + ";charset=utf-8";
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

    private void setMediaCover(String username, String fileName, String relativePath, Update update) {
        String imagePath = videoProcessService.getVideoCover(username, relativePath, fileName);
        if (!CharSequenceUtil.isBlank(imagePath)) {
            if (update == null) {
                update = new Update();
            }
            videoProcessService.convertToM3U8(username, relativePath, fileName);
            update.set("mediaCover", true);
        } else {
            update.set("mediaCover", false);
        }
    }

    /***
     * 生成缩略图
     * @param file File
     * @param update org.springframework.data.mongodb.core.query.UpdateDefinition
     */
    private void generateThumbnail(File file, Update update) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Thumbnails.Builder<? extends File> thumbnail = Thumbnails.of(file);
            thumbnail.size(256, 256);
            thumbnail.toOutputStream(out);
            FastImageInfo imageInfo = new FastImageInfo(file);
            update.set("w", imageInfo.getWidth());
            update.set("h", imageInfo.getHeight());
            update.set("content", out.toByteArray());
        } catch (UnsupportedFormatException e) {
            log.warn(e.getMessage() + file.getAbsolutePath());
        } catch (Exception e) {
            log.error(e.getMessage() + file.getAbsolutePath());
        }
    }

    /***
     * 给用户推送消息
     * @param username username
     * @param message message
     * @param url url
     */
    public void pushMessage(String username, Object message, String url) {
        WebSocketSession webSocketSession = SocketManager.get(username);
        if (webSocketSession != null) {
            Map<String, Object> headers = new HashMap<>(4);
            headers.put("url", url);
            String userId = userLoginHolder.getUserId();
            if (CharSequenceUtil.isBlank(userId)) {
                userId = userService.getUserIdByUserName(username);
            }
            if (!CharSequenceUtil.isBlank(userId)) {
                long takeUpSpace = occupiedSpace(userId);
                headers.put("space", takeUpSpace);
            }
            if (message == null) {
                message = new Document();
            }
            template.convertAndSendToUser(username, "/queue/update", message, headers);
        }
    }

    public void pushMessageOperationFileError(String username, String message, String operation) {
        JSONObject msg = new JSONObject();
        msg.put("code", -1);
        msg.put("msg", message);
        msg.put("operation", operation);
        pushMessage(username, msg, "operationFile");
    }

    public void pushMessageOperationFileSuccess(String fromPath, String toPath, String username, String operation) {
        JSONObject msg = new JSONObject();
        msg.put("code", 0);
        msg.put("from", fromPath);
        msg.put("to", toPath);
        msg.put("operation", operation);
        pushMessage(username, msg, "operationFile");
    }

    public long occupiedSpace(String userId) {
        long space = 0;
        List<Bson> list = Arrays.asList(
                match(eq(IUserService.USER_ID, userId)),
                group(new BsonNull(), sum(Constants.TOTAL_SIZE, "$size")));
        AggregateIterable<Document> aggregateIterable = mongoTemplate.getCollection(COLLECTION_NAME).aggregate(list);
        Document doc = aggregateIterable.first();
        if (doc != null) {
            space = Convert.toLong(doc.get(Constants.TOTAL_SIZE), 0L);
        }
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
            return;
        }
        List<Document> list = Arrays.asList(new Document("$match", new Document("$or", documentList)), new Document("$match", new Document(Constants.SHARE_BASE, true)));
        AggregateIterable<Document> result = mongoTemplate.getCollection(COLLECTION_NAME).aggregate(list);
        Document shareDocument = null;
        try (MongoCursor<Document> mongoCursor = result.iterator()) {
            while (mongoCursor.hasNext()) {
                shareDocument = mongoCursor.next();
            }
        }
        if (shareDocument != null) {
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
            setShareAttribute(update, expiresAt, shareId, isPrivacy, extractionCode);
        }
    }

    /***
     * 设置共享属性
     * @param fileDocument FileDocument
     * @param expiresAt 过期时间
     * @param query 查询条件
     */
    void setShareAttribute(FileDocument fileDocument, long expiresAt, ShareDO share, Query query) {
        Update update = new Update();
        setShareAttribute(update, expiresAt, share.getId(), share.getIsPrivacy(), share.getExtractionCode());
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
    private static void setShareAttribute(Update update, long expiresAt, String shareId, Boolean isPrivacy, String extractionCode) {
        update.set("isShare", true);
        update.set(Constants.SHARE_ID, shareId);
        update.set(Constants.EXPIRES_AT, expiresAt);
        update.set(Constants.IS_PRIVACY, isPrivacy);
        if (BooleanUtil.isTrue(isPrivacy)) {
            update.set(Constants.EXTRACTION_CODE, extractionCode);
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
        update.unset("isShare");
        update.unset(Constants.EXPIRES_AT);
        update.unset(Constants.IS_PRIVACY);
        update.unset(Constants.EXTRACTION_CODE);
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
        // 修改第一个文件/文件夹
        updateShareFirst(fileDocument, update, false);
    }

    public String modifyFile(String username, File file) {
        String fileAbsolutePath = file.getAbsolutePath();
        String fileName = file.getName();
        String relativePath = fileAbsolutePath.substring(fileProperties.getRootDir().length() + username.length() + 1, fileAbsolutePath.length() - fileName.length());
        String userId = userService.getUserIdByUserName(username);
        if (CharSequenceUtil.isBlank(userId)) {
            return null;
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
            pushMessage(username, fileDocument, "updateFile");
            if (null != updateResult.getUpsertedId()) {
                return updateResult.getUpsertedId().asObjectId().getValue().toHexString();
            }
        }
        return null;
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

}

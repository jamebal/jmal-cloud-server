package com.jmal.clouddisk.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.media.VideoInfo;
import com.jmal.clouddisk.media.VideoProcessService;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.oss.web.WebOssCommonService;
import com.jmal.clouddisk.oss.web.WebOssCopyFileService;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.util.*;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import com.mongodb.client.AggregateIterable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.commons.compress.utils.Lists;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.bson.BsonNull;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mozilla.universalchardet.ReaderFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.jmal.clouddisk.service.IUserService.USER_ID;


/**
 * @author jmal
 * @Description 文件管理
 * @Author jmal
 * @Date 2020-01-14 13:05
 */
@Service
@Slf4j
public class FileServiceImpl extends CommonFileService implements IFileService {

    @Autowired
    MultipartUpload multipartUpload;

    @Autowired
    WebOssService webOssService;

    @Autowired
    WebOssCopyFileService webOssCopyFileService;

    @Autowired
    VideoProcessService videoProcessService;

    @Autowired
    TagService tagService;

    private static final AES aes = SecureUtil.aes();

    @Autowired
    private LogService logService;

    @Override
    public ResponseResult<Object> listFiles(UploadApiParamDTO upload) throws CommonException {
        ResponseResult<Object> result = ResultUtil.genResult();
        Path path = Paths.get(upload.getUsername(), upload.getCurrentDirectory());
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            return webOssService.searchFileAndOpenOssFolder(path, upload);
        }
        String currentDirectory = getUserDirectory(upload.getCurrentDirectory());

        if (!CharSequenceUtil.isBlank(upload.getFolder()) && checkMountParam(upload, currentDirectory)) {
            return listFiles(upload);
        }

        Criteria criteria;
        String queryFileType = upload.getQueryFileType();
        if (!CharSequenceUtil.isBlank(queryFileType)) {
            criteria = switch (upload.getQueryFileType()) {
                case Constants.AUDIO -> Criteria.where(Constants.CONTENT_TYPE).regex("^" + Constants.AUDIO);
                case Constants.VIDEO -> Criteria.where(Constants.CONTENT_TYPE).regex("^" + Constants.VIDEO);
                case Constants.CONTENT_TYPE_IMAGE -> Criteria.where(Constants.CONTENT_TYPE).regex("^image");
                case "text" -> Criteria.where(Constants.SUFFIX).in(fileProperties.getSimText());
                case Constants.DOCUMENT -> Criteria.where(Constants.SUFFIX).in(fileProperties.getDocument());
                case "trash" -> new Criteria();
                default -> Criteria.where("path").is(currentDirectory);
            };
        } else {
            criteria = getOtherCriteria(upload, currentDirectory);
        }
        List<FileIntroVO> list = getFileDocuments(upload, criteria);
        result.setData(list);
        result.setCount(getFileDocumentsCount(upload, criteria));
        if (upload.getProps() != null) {
            result.setProps(upload.getProps());
        }
        return result;
    }

    private static Criteria getOtherCriteria(UploadApiParamDTO upload, String currentDirectory) {
        Criteria criteria;
        criteria = Criteria.where("path").is(currentDirectory);
        if (currentDirectory.length() < 2) {
            Boolean isFolder = upload.getIsFolder();
            if (isFolder != null) {
                criteria = Criteria.where(Constants.IS_FOLDER).is(isFolder);
            }
            Boolean isFavorite = upload.getIsFavorite();
            if (isFavorite != null) {
                criteria = Criteria.where(Constants.IS_FAVORITE).is(isFavorite);
            }
            String tagId = upload.getTagId();
            if (CharSequenceUtil.isNotBlank(tagId)) {
                criteria = Criteria.where("tags.tagId").is(tagId);
            }
        }
        return criteria;
    }

    /**
     * 查看是否有挂载文件
     *
     * @param upload           上传参数
     * @param currentDirectory 当前目录
     * @return 是否有挂载文件
     */
    private boolean checkMountParam(UploadApiParamDTO upload, String currentDirectory) {
        if (!CharSequenceUtil.isBlank(currentDirectory)) {
            Path currentDirectoryPath = Paths.get(currentDirectory);
            if (currentDirectoryPath.getFileName() == null) {
                return false;
            }
            String fileId = upload.getFolder();
            FileDocument fileDocument = getById(fileId);
            if (fileDocument != null) {
                upload.setUserId(fileDocument.getUserId());
                Map<String, Object> props = new HashMap<>(2);
                String username = userService.getUserNameById(fileDocument.getUserId());
                props.put("fileUsername", username);
                upload.setProps(props);
                upload.setUsername(username);
                upload.setCurrentDirectory(fileDocument.getPath() + fileDocument.getName());
                upload.setFolder(null);
                return true;
            }
        }
        return false;
    }

    @Override
    public long takeUpSpace(String userId) throws CommonException {
        return occupiedSpace(userId);
    }

    /***
     * 通过查询条件获取文件数
     * @param upload UploadApiParamDTO
     * @param criteriaList criteriaList
     * @return 文件数
     */
    private long getFileDocumentsCount(UploadApiParamDTO upload, Criteria... criteriaList) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USER_ID).is(upload.getUserId()));
        for (Criteria criteria : criteriaList) {
            query.addCriteria(criteria);
        }
        String collectionName = BooleanUtil.isTrue(upload.getIsTrash()) ? TRASH_COLLECTION_NAME : COLLECTION_NAME;
        if (TRASH_COLLECTION_NAME.equals(collectionName)) {
            query.addCriteria(Criteria.where("hidden").is(false));
        }
        return mongoTemplate.count(query, collectionName);
    }

    private List<FileIntroVO> getFileDocuments(UploadApiParamDTO upload, Criteria... criteriaList) {
        List<FileIntroVO> fileIntroVOList;
        Query query = getQuery(upload, criteriaList);
        String order = listByPage(upload, query);
        if (!CharSequenceUtil.isBlank(order)) {
            String sortableProp = upload.getSortableProp();
            Sort.Direction direction = Sort.Direction.ASC;
            if ("descending".equals(order)) {
                direction = Sort.Direction.DESC;
            }
            query.with(Sort.by(direction, sortableProp));
        } else {
            query.with(Sort.by(Sort.Direction.DESC, Constants.IS_FOLDER));
        }
        query.fields().exclude("content").exclude("music.coverBase64");
        String collectionName = BooleanUtil.isTrue(upload.getIsTrash()) ? TRASH_COLLECTION_NAME : COLLECTION_NAME;
        if (TRASH_COLLECTION_NAME.equals(collectionName)) {
            query.addCriteria(Criteria.where("hidden").is(false));
        }
        List<FileDocument> list = mongoTemplate.find(query, FileDocument.class, collectionName);
        long now = System.currentTimeMillis();
        fileIntroVOList = list.parallelStream().map(fileDocument -> {
            LocalDateTime updateDate = fileDocument.getUpdateDate();
            long update = TimeUntils.getMilli(updateDate);
            fileDocument.setAgoTime(now - update);
            if (showFolderSize(upload, fileDocument)) {
                String path = fileDocument.getPath() + fileDocument.getName() + File.separator;
                long size = getFolderSize(collectionName, fileDocument.getUserId(), path);
                fileDocument.setSize(size);
            }
            FileIntroVO fileIntroVO = new FileIntroVO();
            BeanUtils.copyProperties(fileDocument, fileIntroVO);
            return fileIntroVO;
        }).toList();
        pushConfigInfo(upload);
        return sortByFileName(upload, fileIntroVOList, order);
    }

    /**
     * 是否显示文件夹大小
     *
     * @param upload       UploadApiParamDTO
     * @param fileDocument FileDocument
     */
    private static boolean showFolderSize(UploadApiParamDTO upload, FileDocument fileDocument) {
        return BooleanUtil.isTrue(fileDocument.getIsFolder()) && BooleanUtil.isTrue(upload.getShowFolderSize());
    }

    private void pushConfigInfo(UploadApiParamDTO upload) {
        pushMessage(upload.getUsername(), Constants.LOCAL_CHUNK_SIZE, Constants.UPLOADER_CHUNK_SIZE);
    }

    /***
     * 设置分页条件
     * @return 排序条件
     */
    public static String listByPage(UploadApiParamDTO upload, Query query) {
        Integer pageSize = upload.getPageSize();
        Integer pageIndex = upload.getPageIndex();
        if (pageSize != null && pageIndex != null) {
            long skip = (pageIndex - 1L) * pageSize;
            query.skip(skip);
            query.limit(pageSize);
        }
        return upload.getOrder();
    }

    private List<FileDocument> getDirDocuments(UploadApiParamDTO upload, Criteria... criteriaList) {
        Query query = getQuery(upload, criteriaList);
        query.addCriteria(Criteria.where(Constants.IS_FOLDER).is(true));
        List<FileDocument> list = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        // 按文件名排序
        list.sort(this::compareByFileName);
        return list;
    }

    private Query getQuery(UploadApiParamDTO upload, Criteria[] criteriaList) {
        String userId = upload.getUserId();
        if (CharSequenceUtil.isBlank(userId)) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg() + USER_ID);
        }
        Query query = new Query();
        query.addCriteria(Criteria.where(USER_ID).is(userId));
        for (Criteria criteria : criteriaList) {
            query.addCriteria(criteria);
        }
        return query;
    }

    @Override
    public ResponseResult<List<FileIntroVO>> searchFile(UploadApiParamDTO upload, String keyword) throws CommonException {
        SearchDTO searchDTO = new SearchDTO();
        searchDTO.setKeyword(keyword);
        searchDTO.setPage(upload.getPageIndex());
        searchDTO.setPageSize(upload.getPageSize());
        searchDTO.setSortProp(upload.getSortableProp());
        searchDTO.setSortOrder(upload.getOrder());
        searchDTO.setUserId(userLoginHolder.getUserId());
        searchDTO.setCurrentDirectory(upload.getCurrentDirectory());
        searchDTO.setIsFolder(upload.getIsFolder());
        searchDTO.setType(upload.getQueryFileType());
        searchDTO.setIsFavorite(upload.getIsFavorite());
        searchDTO.setTagId(upload.getTagId());
        searchDTO.setFolder(upload.getFolder());
        return luceneService.searchFile(searchDTO);
    }

    private ResponseResult<Object> getCountResponseResult(UploadApiParamDTO upload, ResponseResult<Object> result, Criteria... criteriaList) {
        List<FileIntroVO> list = getFileDocuments(upload, criteriaList);
        result.setData(list);
        result.setCount(getFileDocumentsCount(upload, criteriaList));
        return result;
    }

    @Override
    public ResponseResult<Object> searchFileAndOpenDir(UploadApiParamDTO upload, String id, String folder) throws CommonException {
        ResponseResult<Object> result = ResultUtil.genResult();

        setMountAttributes(upload, id, folder);

        // 判断是否为ossPath
        Path path = Paths.get(upload.getUsername(), upload.getCurrentDirectory());
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            return webOssService.searchFileAndOpenOssFolder(path, upload);
        }
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument == null) {
            return ResultUtil.error(ExceptionType.FILE_NOT_FIND);
        }
        if (!fileDocument.getUserId().equals(userLoginHolder.getUserId())) {
            Map<String, Object> props = new HashMap<>(2);
            String username = userService.getUserNameById(fileDocument.getUserId());
            props.put("fileUsername", username);
            result.setProps(props);
        }
        String currentDirectory = getUserDirectory(fileDocument.getPath() + fileDocument.getName());

        // 判断是否为ossPath
        path = Paths.get(upload.getUsername(), currentDirectory);
        ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            return webOssService.searchFileAndOpenOssFolder(path, upload);
        }

        Criteria criteria = Criteria.where("path").is(currentDirectory);
        upload.setUserId(fileDocument.getUserId());
        return getCountResponseResult(upload, result, criteria);
    }

    /**
     * 设置挂载属性
     */
    private static void setMountAttributes(UploadApiParamDTO upload, String id, String folder) {
        if (CharSequenceUtil.isNotBlank(folder)) {
            Path path = Paths.get(folder);
            String username = path.subpath(0, 1).toString();
            String ossPath = CaffeineUtil.getOssPath(Paths.get(id));
            if (ossPath != null) {
                upload.setCurrentDirectory(path.subpath(1, path.getNameCount()).toString());
            }
            upload.setUsername(username);
        }
    }

    private FileDocument getFileDocumentById(String fileId) {
        if (CharSequenceUtil.isBlank(fileId) || Constants.REGION_DEFAULT.equals(fileId)) {
            return null;
        }
        return mongoTemplate.findById(fileId, FileDocument.class, COLLECTION_NAME);
    }

    private FileDocument getOriginalFileDocumentById(String fileId) {
        FileDocument fileDocument = getFileDocumentById(fileId);
        if (fileDocument != null && fileDocument.getMountFileId() != null) {
            fileDocument = mongoTemplate.findById(fileDocument.getMountFileId(), FileDocument.class, COLLECTION_NAME);
        }
        return fileDocument;
    }

    /**
     * 获取文件的相对路径
     *
     * @param fileDocument FileDocument
     * @return 相对路径
     */
    private String getRelativePath(FileDocument fileDocument) {
        if (fileDocument == null) {
            return getUserDirectory(null);
        }
        if (Boolean.TRUE.equals(fileDocument.getIsFolder())) {
            return getUserDirectory(fileDocument.getPath() + fileDocument.getName());
        }
        String currentDirectory = fileDocument.getPath() + fileDocument.getName();
        return currentDirectory.replaceAll(fileProperties.getSeparator(), File.separator);
    }

    /***
     * 获取用户的绝对目录
     * @param username username
     */
    private String getUserDir(String username) {
        return Paths.get(fileProperties.getRootDir(), username).toString();
    }


    @Override
    public ResponseResult<Object> queryFileTree(UploadApiParamDTO upload, String fileId) {
        // 设置是否只显示文件夹
        upload.setJustShowFolder(true);
        if (!CharSequenceUtil.isBlank(fileId) && BooleanUtil.isFalse(upload.getHideMountFile())) {
            Path path = Paths.get(fileId);
            String ossPath = CaffeineUtil.getOssPath(path);
            if (ossPath != null) {
                return webOssService.searchFileAndOpenOssFolder(path, upload);
            }
        }

        String currentDirectory = getUserDirectory(null);
        if (!CharSequenceUtil.isBlank(fileId)) {
            FileDocument fileDocument = getById(fileId);
            if (fileDocument != null) {
                if (fileDocument.getOssFolder() != null && BooleanUtil.isFalse(upload.getHideMountFile())) {
                    Path path = Paths.get(upload.getUsername(), fileDocument.getOssFolder());
                    String ossPath = CaffeineUtil.getOssPath(path);
                    if (ossPath != null) {
                        return webOssService.searchFileAndOpenOssFolder(path, upload);
                    }
                }
                if (!fileDocument.getUserId().equals(userLoginHolder.getUserId())) {
                    upload.setUserId(fileDocument.getUserId());
                    upload.setUsername(userService.getUserNameById(fileDocument.getUserId()));
                }
                currentDirectory = getUserDirectory(fileDocument.getPath() + fileDocument.getName());
            }
        }
        Criteria criteria = Criteria.where("path").is(currentDirectory);
        List<FileDocument> list = getDirDocuments(upload, criteria);
        if (BooleanUtil.isTrue(upload.getHideMountFile())) {
            list = list.stream().filter(fileDocument -> fileDocument.getMountFileId() == null).toList();
        }
        return ResultUtil.success(list);
    }

    @Override
    public String imgUpload(String baseUrl, String filepath, MultipartFile file) {
        String username = userLoginHolder.getUsername();
        String fileName = file.getOriginalFilename();
        Path path = Paths.get(fileProperties.getRootDir(), username, filepath, fileName);
        try {
            File newFile = path.toFile();
            FileUtil.writeFromStream(file.getInputStream(), newFile);
            loopCreateDir(username, Paths.get(fileProperties.getRootDir(), username).getNameCount(), path);
            if (!userService.getDisabledWebp(userLoginHolder.getUserId()) && (!"ico".equals(FileUtil.getSuffix(newFile)))) {
                fileName += Constants.POINT_SUFFIX_WEBP;
            }
            uploadFile(username, newFile);
            return baseUrl + Paths.get("/file", username, filepath, fileName);
        } catch (IOException e) {
            throw new CommonException(ExceptionType.FAIL_UPLOAD_FILE.getCode(), ExceptionType.FAIL_UPLOAD_FILE.getMsg());
        }
    }

    /***
     * 递归创建父级目录(数据库层面)
     * @param username username
     * @param path path
     */
    private void loopCreateDir(String username, int rootPathCount, Path path) {
        createFile(username, path.toFile(), userLoginHolder.getUserId(), true);
        if (path.getNameCount() > rootPathCount + 1) {
            loopCreateDir(username, rootPathCount, path.getParent());
        }
    }

    /***
     * 统计文件夹的大小
     */
    private long getFolderSize(String collectionName, String userId, String path) {
        List<Bson> list = Arrays.asList(new Document("$match", new Document(USER_ID, userId).append(Constants.IS_FOLDER, false).append("path", new Document("$regex", "^" + ReUtil.escape(path)))), new Document("$group", new Document("_id", new BsonNull()).append(Constants.TOTAL_SIZE, new Document("$sum", "$size"))));
        AggregateIterable<Document> result = mongoTemplate.getCollection(collectionName).aggregate(list);
        long totalSize = 0;
        Document doc = result.first();
        if (doc != null) {
            Object object = doc.get(Constants.TOTAL_SIZE);
            if (object != null) {
                totalSize = Long.parseLong(object.toString());
            }
        }
        return totalSize;
    }

    @Override
    public Optional<FileDocument> getById(String id, Boolean content) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            String currentDirectory = getUserDirectory(fileDocument.getPath());
            String username = userService.getUserNameById(fileDocument.getUserId());
            Path filepath = Paths.get(fileProperties.getRootDir(), username, currentDirectory, fileDocument.getName());
            if (Files.exists(filepath)) {
                File file = filepath.toFile();
                if (MyFileUtils.hasCharset(file)) {
                    Charset charset = MyFileUtils.getFileCharset(file);
                    fileDocument.setDecoder(charset.name());
                    if (BooleanUtil.isTrue(content)) {
                        fileDocument.setContentText(FileUtil.readString(file, charset));
                    }
                }
            }
            return Optional.of(fileDocument);
        }
        return Optional.empty();
    }

    @Override
    public StreamingResponseBody getStreamById(String id) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            String currentDirectory = getUserDirectory(fileDocument.getPath());
            String username = userService.getUserNameById(fileDocument.getUserId());
            Path filepath = Paths.get(fileProperties.getRootDir(), username, currentDirectory, fileDocument.getName());
            if (Files.exists(filepath)) {
                File file = filepath.toFile();
                return getStreamingResponseBody(file);
            }
        }
        return outputStream -> {
        };
    }

    @Override
    public FileDocument getFileDocumentByPathAndName(String path, String name, String username) {
        String userId = userService.getUserIdByUserName(username);
        if (userId == null) {
            return null;
        }
        return getFileDocumentByPath(path, name, userId);
    }

    @Override
    public FileDocument previewTextByPath(String filePath, String username) throws CommonException {
        Path path = Paths.get(fileProperties.getRootDir(), username, filePath);
        File file = path.toFile();
        if (!file.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        FileDocument fileDocument = new FileDocument();
        if (MyFileUtils.hasCharset(file)) {
            fileDocument.setDecoder(MyFileUtils.getFileCharset(file).name());
        }
        Path path1 = path.subpath(0, path.getNameCount() - 1);
        int rootCount = Paths.get(fileProperties.getRootDir(), username).getNameCount();
        int path1Count = path1.getNameCount();
        String resPath = "/";
        if (rootCount < path1Count) {
            resPath = path1.subpath(rootCount, path1Count).toString();
        }
        fileDocument.setPath(resPath);
        fileDocument.setName(file.getName());
        fileDocument.setIsFolder(file.isDirectory());
        return fileDocument;
    }

    @Override
    public StreamingResponseBody previewTextByPathStream(String filePath, String username) {
        Path path = Paths.get(fileProperties.getRootDir(), username, filePath);
        File file = path.toFile();
        if (!file.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        return getStreamingResponseBody(file);
    }

    @NotNull
    private static StreamingResponseBody getStreamingResponseBody(File file) {
        return outputStream -> {
            try (BufferedReader bufferedReader = ReaderFactory.createBufferedReader(file)) {
                // 判断file是否为log文件
                boolean logFile = file.length() > 0 && FileTypeUtil.getType(file).equalsIgnoreCase("log");
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (logFile) {
                        String processedLine = removeAnsiCodes(line);
                        outputStream.write(processedLine.getBytes());
                    } else {
                        outputStream.write(line.getBytes());
                    }
                    outputStream.write("\n".getBytes());
                    outputStream.flush();
                }
            } catch (ClientAbortException ignored) {
                // ignored
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        };
    }

    private static String removeAnsiCodes(String text) {
        return text.replaceAll("\\033\\[[;\\d]*[^\\x40-\\x7E]*[a-zA-Z]?", "");
    }

    @Override
    public Optional<FileDocument> thumbnail(String id, Boolean showCover) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {

            String username = userService.getUserNameById(fileDocument.getUserId());

            if (BooleanUtil.isTrue(showCover) && BooleanUtil.isTrue(fileDocument.getShowCover())) {
                File file = FileContentUtil.getCoverPath(videoProcessService.getVideoCacheDir(username, id));
                if (file.exists()) {
                    fileDocument.setContent(FileUtil.readBytes(file));
                    return Optional.of(fileDocument);
                }
            }
            if (fileDocument.getContent() == null) {
                String currentDirectory = getUserDirectory(fileDocument.getPath());
                File file = new File(fileProperties.getRootDir() + File.separator + username + currentDirectory + fileDocument.getName());
                if (file.exists()) {
                    fileDocument.setContent(FileUtil.readBytes(file));
                }
            }
            return Optional.of(fileDocument);
        }
        return Optional.empty();
    }

    @Override
    public Optional<FileDocument> getMxweb(String id) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            String username = userService.getUserNameById(fileDocument.getUserId());
            File file = Paths.get(videoProcessService.getVideoCacheDir(username, id), fileDocument.getName() + Constants.MXWEB_SUFFIX).toFile();
            if (file.exists()) {
                fileDocument.setContent(FileUtil.readBytes(file));
                return Optional.of(fileDocument);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<FileDocument> coverOfMedia(String id, String username) throws CommonException {
        FileDocument fileDocument = getFileDocumentById(id);
        if (fileDocument != null && fileDocument.getContent() != null) {
            return Optional.of(fileDocument);
        }
        if (Boolean.TRUE.equals(CaffeineUtil.hasThumbnailRequestCache(id))) {
            return Optional.empty();
        } else {
            CaffeineUtil.setThumbnailRequestCache(id);
        }
        String ossPath = CaffeineUtil.getOssPath(Paths.get(id));
        if (ossPath != null) {
            // S3存储
            if (fileDocument == null) {
                FileDocument ossFileDocument = webOssService.getFileDocumentByOssPath(ossPath, id);
                setMediaCover(id, username, ossFileDocument, false);
                fileDocument = ossFileDocument;
            } else {
                setMediaCover(id, username, fileDocument, true);
            }
        } else {
            // 本地储存
            if (fileDocument != null) {
                setMediaCover(id, username, fileDocument, true);
            } else {
                return Optional.empty();
            }
        }
        return Optional.of(fileDocument);
    }

    private void setMediaCover(String id, String username, FileDocument fileDocument, boolean hasOldFileDocument) {
        String contentType = fileDocument.getContentType();
        if (contentType.contains(Constants.VIDEO)) {
            // 视频文件
            Query query = new Query().addCriteria(Criteria.where("_id").is(id));
            VideoInfo videoInfo = videoProcessService.getVideoCover(id, username, fileDocument.getPath(), fileDocument.getName());
            String imagePath = videoInfo.getCovertPath();
            if (!CharSequenceUtil.isBlank(imagePath)) {
                fileDocument.setContent(FileUtil.readBytes(imagePath));
                if (hasOldFileDocument) {
                    Update update = new Update();
                    update.set("content", fileDocument.getContent());
                    mongoTemplate.upsert(query, update, FileDocument.class);
                } else {
                    mongoTemplate.save(fileDocument);
                }
            } else {
                Update update = new Update();
                update.set("mediaCover", false);
                mongoTemplate.updateFirst(query, update, FileDocument.class);
            }
            if (imagePath != null && FileUtil.exist(imagePath)) {
                FileUtil.del(imagePath);
            }
        } else {
            // 音频文件
            String base64 = Optional.of(fileDocument).map(FileDocument::getMusic).map(Music::getCoverBase64).orElse("");
            fileDocument.setContent(Base64.decode(base64));
        }
        fileDocument.setContentType("image/png");
        fileDocument.setName("cover");
    }

    @Override
    public void publicPackageDownload(HttpServletRequest request, HttpServletResponse response, List<String> fileIdList) {
        packageDownload(request, response, fileIdList, null);
    }

    @Override
    public void packageDownload(HttpServletRequest request, HttpServletResponse response, List<String> fileIdList) {
        String username = request.getParameter(AuthInterceptor.NAME_HEADER);
        packageDownload(request, response, fileIdList, username);
    }

    private void packageDownload(HttpServletRequest request, HttpServletResponse response, List<String> fileIdList, String username) {
        FileDocument fileDocument = getFileInfoBeforeDownload(fileIdList, username);
        if (fileDocument == null) {
            return;
        }
        //响应头的设置
        response.reset();
        response.setCharacterEncoding("utf-8");
        response.setContentType("multipart/form-data");
        //设置压缩包的名字
        setDownloadName(request, response, fileDocument.getName() + ".zip");

        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIdList));
        List<FileDocument> fileDocuments = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        // 选中的文件
        List<Path> selectFileList = fileDocuments.stream().map(fileDoc -> {
            String fileUsername = userService.getUserNameById(fileDoc.getUserId());
            return Paths.get(fileProperties.getRootDir(), fileUsername, fileDoc.getPath(), fileDoc.getName());
        }).toList();
        // 压缩传输
        try {
            CompressUtils.compress(selectFileList, response.getOutputStream());
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    /***
     * 对下载的文件名转码 解决不同浏览器压缩包名字含有中文时乱码的问题
     * @param request HttpServletRequest
     * @param downloadName downloadName
     */
    private void setDownloadName(HttpServletRequest request, HttpServletResponse response, String downloadName) {
        //获取浏览器名（IE/Chrome/firefox）目前主流的四大浏览器内核Trident(IE)、Gecko(Firefox内核)、WebKit(Safari内核,Chrome内核原型,开源)以及Presto(Opera前内核) (已废弃)
        String gecko = "Gecko";
        String webKit = "WebKit";
        String userAgent = request.getHeader("User-Agent");
        if (userAgent.contains(gecko) || userAgent.contains(webKit)) {
            downloadName = new String(downloadName.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        } else {
            downloadName = URLEncoder.encode(downloadName, StandardCharsets.UTF_8);
        }
        response.setHeader("Content-Disposition", "attachment;fileName=\"" + downloadName + "\"");
    }

    /**
     * 打包下载之前要干的事
     *
     * @param fileIds  fileIds
     * @param username username
     */
    private FileDocument getFileInfoBeforeDownload(List<String> fileIds, String username) throws CommonException {
        String fileId = fileIds.get(0);
        // 判断是否为ossPath
        Path path = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "暂不支持打包下载");
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileId));
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
        if (fileDocument == null) {
            return null;
        }
        if (CharSequenceUtil.isBlank(username)) {
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
            if (size == 1 && Boolean.TRUE.equals(!fileDocument.getIsFolder())) {
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
    public ResponseResult<Object> rename(String newFileName, String username, String id, String folder) {
        // 判断是否为ossPath
        List<OperationPermission> operationPermissionList = null;
        if (CharSequenceUtil.isNotBlank(folder)) {
            FileDocument fileDocument = getById(folder);
            String userId = fileDocument.getUserId();
            username = userService.getUserNameById(userId);
            operationPermissionList = fileDocument.getOperationPermissionList();
        }
        checkPermissionUsername(username, operationPermissionList, OperationPermission.PUT);
        String finalUsername = username;
        String operator = userLoginHolder.getUsername();
        LogOperation logOperation = logService.getLogOperation();
        ThreadUtil.execute(() -> {
            try {
                String ossPath = CaffeineUtil.getOssPath(Paths.get(id));
                if (ossPath != null) {
                    // oss 重命名
                    webOssService.rename(ossPath, id, newFileName, operator);
                    return;
                }
                renameFile(newFileName, finalUsername, id, operator, logOperation);
            } catch (CommonException e) {
                pushMessageOperationFileError(finalUsername, Convert.toStr(e.getMsg(), Constants.UNKNOWN_ERROR), "重命名");
            } catch (Exception e) {
                pushMessageOperationFileError(finalUsername, Convert.toStr(e.getMessage(), Constants.UNKNOWN_ERROR), "重命名");
            }
        });
        return ResultUtil.success();
    }

    private void renameFile(String newFileName, String username, String id, String operator, LogOperation logOperation) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        Path fromPath;
        Path toPath;
        if (fileDocument != null) {
            // 判断是否为ossPath根目录
            if (fileDocument.getOssFolder() != null) {
                throw new CommonException(ExceptionType.LOCKED_RESOURCES.getCode(), "请在oss管理中修改目录名称");
            }
            if (CommonFileService.isLock(fileDocument)) {
                throw new CommonException(ExceptionType.LOCKED_RESOURCES);
            }
            String currentDirectory = getUserDirectory(fileDocument.getPath());
            fromPath = Paths.get(currentDirectory, fileDocument.getName());
            toPath = Paths.get(currentDirectory, newFileName);
            String filePath = fileProperties.getRootDir() + File.separator + username + currentDirectory;
            File file = new File(filePath + fileDocument.getName());
            if (Boolean.TRUE.equals(fileDocument.getIsFolder())) {
                Query query = new Query();
                String searchPath = currentDirectory + fileDocument.getName();
                String newPath = currentDirectory + newFileName;
                query.addCriteria(Criteria.where(USER_ID).is(userService.getUserIdByUserName(username)));
                query.addCriteria(Criteria.where("path").regex("^" + ReUtil.escape(searchPath)));
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
                    luceneService.pushCreateIndexQueue(rep.getId());
                });
            }
            if (renameFileError(newFileName, id, filePath, file)) {
                pushMessageOperationFileError(operator, "重命名失败", "重命名");
                return;
            }
            String oldName = fileDocument.getName();
            fileDocument.setName(newFileName);
            pushMessage(operator, fileDocument, Constants.CREATE_FILE);

            // 记录日志
            logService.addLogFileOperation(logOperation, username, Paths.get(fileDocument.getPath(), fileDocument.getName()).toString(), "重命名, 从\"" + oldName + "\"到\"" + newFileName + "\"");

        } else {
            pushMessageOperationFileError(operator, "重命名失败", "重命名");
            return;
        }
        pushMessageOperationFileSuccess(fromPath.toString(), toPath.toString(), operator, "重命名");
        afterRenameFile(id, newFileName);
    }

    /**
     * 重命名文件后修改关联配置
     *
     * @param fileId      fileId
     * @param newFileName newFileName
     */
    private void afterRenameFile(String fileId, String newFileName) {
        // 修改关联的分享配置
        Query shareQuery = new Query();
        shareQuery.addCriteria(Criteria.where("fileId").is(fileId));
        Update shareUpdate = new Update();
        shareUpdate.set("fileName", newFileName);
        mongoTemplate.updateMulti(shareQuery, shareUpdate, ShareDO.class);
        // 修改关联的挂载配置
        Query mountQuery = new Query();
        mountQuery.addCriteria(Criteria.where("mountFileId").is(fileId));
        Update mountUpdate = new Update();
        mountUpdate.set("name", newFileName);
        mongoTemplate.updateMulti(mountQuery, mountUpdate, FileDocument.class);
        luceneService.pushCreateIndexQueue(fileId);
    }

    @Override
    public ResponseResult<List<FileDocument>> checkMoveOrCopy(UploadApiParamDTO upload, List<String> froms, String to) {
        if (CharSequenceUtil.isBlank(to) && CharSequenceUtil.isBlank(upload.getTargetPath())) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS);
        }
        List<String> fromFilenameList = getFromFilenameList(froms);

        String ossPathTo = getPreOssPath(upload, to);
        if (ossPathTo != null) {
            return ResultUtil.success();
        }

        FileDocument toFileDocument = getToFileDocument(upload, to);
        if (toFileDocument == null) {
            return ResultUtil.error(ExceptionType.FILE_NOT_FIND);
        }
        String toPath = getRelativePath(toFileDocument);
        Query query = new Query();
        query.addCriteria(Criteria.where(USER_ID).is(toFileDocument.getUserId()));
        query.addCriteria(Criteria.where("path").is(toPath));
        query.addCriteria(Criteria.where("name").in(fromFilenameList));
        List<FileDocument> fileDocuments = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        // 只保留name, path, suffix
        fileDocuments.forEach(fileDocument -> {
            fileDocument.setIsShare(null);
            fileDocument.setShareId(null);
            fileDocument.setShareBase(null);
            fileDocument.setIsFavorite(null);
            fileDocument.setTags(null);
            fileDocument.setMountFileId(null);
            fileDocument.setOssFolder(null);
        });
        return ResultUtil.success(fileDocuments).setCount(fileDocuments.size());
    }

    @Nullable
    private static String getPreOssPath(UploadApiParamDTO upload, String to) {
        Path prePath;
        if (!CharSequenceUtil.isBlank(to)) {
            prePath = Paths.get(to);
        } else {
            prePath = Paths.get(upload.getUsername(), upload.getTargetPath());
        }
        return CaffeineUtil.getOssPath(prePath);
    }

    private List<String> getFromFilenameList(List<String> froms) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(froms));
        query.fields().include("name");
        return mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME).stream().map(FileDocument::getName).toList();
    }

    @Override
    public ResponseResult<Object> move(UploadApiParamDTO upload, List<String> froms, String to) throws IOException {
        // 复制
        upload.setUserId(userLoginHolder.getUserId());
        upload.setUsername(userLoginHolder.getUsername());
        LogOperation logOperation = logService.getLogOperation();
        ThreadUtil.execute(() -> {
            try {
                String currentDirectory = getOssFileCurrentDirectory(upload, froms);
                String fromFileIdOne = froms.get(0);
                FileDocument formFileDocument = getOriginalFileDocumentById(fromFileIdOne);
                if (formFileDocument == null) {
                    String ossPathFormOne = CaffeineUtil.getOssPath(Paths.get(fromFileIdOne));
                    if (ossPathFormOne != null) {
                        // 移动
                        move(upload, froms, to, currentDirectory, logOperation);
                    }
                    return;
                }
                String formUsername = userService.getUserNameById(formFileDocument.getUserId());
                if (!upload.getUsername().equals(formUsername)) {
                    checkPermissionUsername(formUsername, formFileDocument.getOperationPermissionList(), OperationPermission.DELETE);
                }
                // 移动
                move(upload, froms, to, currentDirectory, logOperation);
            } catch (CommonException e) {
                if (e.getCode() != -3) {
                    pushMessageOperationFileError(upload.getUsername(), Convert.toStr(e.getMsg(), Constants.UNKNOWN_ERROR), "移动");
                }
            } catch (Exception e) {
                pushMessageOperationFileError(upload.getUsername(), Convert.toStr(e.getMessage(), Constants.UNKNOWN_ERROR), "移动");
            }
        });
        return ResultUtil.success();
    }

    private void move(UploadApiParamDTO upload, List<String> froms, String to, String currentDirectory, LogOperation logOperation) {
        // 复制
        getCopyResult(upload, froms, to, true, logOperation);
        // 删除
        deleteOnlyDoc(upload.getUsername(), currentDirectory, froms, upload.getUsername());
    }

    private String getOssFileCurrentDirectory(UploadApiParamDTO upload, List<String> froms) {
        String currentDirectory = "/";
        String from = froms.get(0);
        FileDocument fileDocumentFrom = getFileDocumentById(from);
        if (fileDocumentFrom != null && fileDocumentFrom.getOssFolder() != null) {
            from = upload.getUsername() + MyWebdavServlet.PATH_DELIMITER + fileDocumentFrom.getOssFolder() + MyWebdavServlet.PATH_DELIMITER;
        }
        Path fromPath = Paths.get(from);
        if (CaffeineUtil.getOssPath(fromPath) != null) {
            currentDirectory = fromPath.toString().substring(upload.getUsername().length());
        }
        return currentDirectory;
    }

    private void getCopyResult(UploadApiParamDTO upload, List<String> froms, String to, boolean move, LogOperation logOperation) {
        for (String from : froms) {
            ResponseResult<Object> result;
            try {
                result = copyOrMove(upload, from, to, move, logOperation);
            } catch (CommonException e) {
                throw e;
            } catch (Exception e) {
                throw new CommonException(ExceptionType.SYSTEM_ERROR);
            }
            if (result != null && result.getCode() != 0) {
                throw new CommonException(result.getCode(), Convert.toStr(result.getMessage(), Constants.UNKNOWN_ERROR));
            }
        }
    }

    @Override
    public ResponseResult<Object> copy(UploadApiParamDTO upload, List<String> froms, String to) throws IOException {
        // 复制
        upload.setUserId(userLoginHolder.getUserId());
        upload.setUsername(userLoginHolder.getUsername());
        LogOperation logOperation = logService.getLogOperation();
        ThreadUtil.execute(() -> {
            try {
                // 复制成功
                getCopyResult(upload, froms, to, false, logOperation);
            } catch (CommonException e) {
                pushMessageOperationFileError(upload.getUsername(), Convert.toStr(e.getMsg(), Constants.UNKNOWN_ERROR), "复制");
            } catch (Exception e) {
                pushMessageOperationFileError(upload.getUsername(), Convert.toStr(e.getMessage(), Constants.UNKNOWN_ERROR), "复制");
            }
        });
        return ResultUtil.success();
    }

    @Override
    public String uploadConsumerImage(UploadApiParamDTO upload) throws CommonException {
        MultipartFile multipartFile = upload.getFile();
        String username = upload.getUsername();
        String userId = upload.getUserId();
        String fileName = upload.getFilename();
        MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
        MimeType mimeType;
        try {
            mimeType = allTypes.forName(multipartFile.getContentType());
            fileName += mimeType.getExtension();
        } catch (MimeTypeException e) {
            log.error(e.getMessage(), e);
        }
        Path userImagePaths = Paths.get(fileProperties.getUserImgDir());
        // userImagePaths 不存在则新建
        upsertFolder(userImagePaths, username, userId);
        File newFile;
        try {
            if (userService.getDisabledWebp(userId) || ("ico".equals(FileUtil.getSuffix(fileName)))) {
                newFile = Paths.get(fileProperties.getRootDir(), username, userImagePaths.toString(), fileName).toFile();
                FileUtil.writeFromStream(multipartFile.getInputStream(), newFile);
            } else {
                fileName = fileName + Constants.POINT_SUFFIX_WEBP;
                newFile = Paths.get(fileProperties.getRootDir(), username, userImagePaths.toString(), fileName).toFile();
                BufferedImage image = ImageIO.read(multipartFile.getInputStream());
                imageFileToWebp(newFile, image);
            }
        } catch (IOException e) {
            throw new CommonException(2, "上传失败");
        }
        return createFile(username, newFile, userId, true);
    }

    @Override
    public FileDocument getById(String fileId) {
        FileDocument fileDocument = getFileDocumentById(fileId);
        if (fileDocument != null) {
            return fileDocument;
        }
        String ossPath = CaffeineUtil.getOssPath(Paths.get(fileId));
        if (ossPath != null) {
            return webOssService.getFileDocumentByOssPath(ossPath, fileId);
        }
        return null;
    }

    @Override
    public List<FileDocument> listByIds(List<String> fileIdList) {
        return mongoTemplate.find(Query.query(Criteria.where("_id").in(fileIdList)), FileDocument.class, COLLECTION_NAME);
    }

    @Override
    public String createFile(String username, File file) {
        return createFile(username, file, null, null);
    }

    private String uploadFile(String username, File file) {
        CaffeineUtil.setUploadFileCache(file.getAbsolutePath());
        return createFile(username, file, null, null);
    }

    @Override
    public void updateFile(String username, File file) {
        modifyFile(username, file);
    }

    @Override
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
                luceneService.deleteIndexDocuments(Collections.singletonList(fileDocument.getId()));
            }
        }
        pushMessage(username, relativePath, Constants.DELETE_FILE);
    }

    @Override
    public ResponseResult<Object> unzip(String fileId, String destFileId) throws CommonException {
        try {
            String ossPath = CaffeineUtil.getOssPath(Paths.get(fileId));
            if (ossPath != null) {
                return ResultUtil.warning("暂不支持解压!");
            }
            FileDocument fileDocument = getById(fileId);
            if (fileDocument == null) {
                throw new CommonException(ExceptionType.FILE_NOT_FIND);
            }
            String username = userService.getUserNameById(fileDocument.getUserId());
            if (CharSequenceUtil.isBlank(username)) {
                throw new CommonException(ExceptionType.USER_NOT_FIND);
            }
            String filePath = getFilePathByFileId(username, fileDocument);
            String destDir;
            boolean isWrite = false;
            String desc = "";
            if (CharSequenceUtil.isBlank(destFileId)) {
                // 没有目标目录, 则预览解压到临时目录
                destDir = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, fileDocument.getName()).toString();
            } else {
                if (fileId.equals(destFileId)) {
                    // 解压到当前文件夹
                    destDir = filePath.substring(0, filePath.length() - MyFileUtils.extName(new File(filePath)).length() - 1);
                    desc = "解压文件, 目标目录: " + Paths.get(fileDocument.getPath());
                } else {
                    // 其他目录
                    FileDocument dest = getById(destFileId);
                    if (dest != null) {
                        destDir = getFilePathByFileId(username, dest);
                        desc = "解压文件, 目标目录: " + Paths.get(dest.getPath());
                    } else {
                        destDir = Paths.get(fileProperties.getRootDir(), username).toString();
                        desc = "解压文件, 目标目录: /";
                    }
                }
                isWrite = true;
            }
            CompressUtils.decompress(filePath, destDir, isWrite);
            if (isWrite) {
                logService.addLogFileOperation(username, Paths.get(fileDocument.getPath(), fileDocument.getName()).toString(), desc);
            }
            return ResultUtil.success(listFile(username, destDir, !isWrite));
        } catch (CommonException e) {
            return ResultUtil.warning(e.getMessage());
        } catch (Exception e) {
            return ResultUtil.error("解压失败!");
        }
    }

    @Override
    public ResponseResult<Object> listFiles(String path, String username, boolean tempDir) {
        Path prePth = Paths.get(username, path);
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            UploadApiParamDTO uploadApiParamDTO = new UploadApiParamDTO();
            uploadApiParamDTO.setPathAttachFileName(true);
            uploadApiParamDTO.setUsername(username);
            return webOssService.searchFileAndOpenOssFolder(prePth, uploadApiParamDTO);
        }
        String dirPath;
        if (tempDir) {
            dirPath = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, path).toString();
            return ResultUtil.success(listFile(username, dirPath, true));
        } else {
            return ResultUtil.success(listFile(path, userLoginHolder.getUserId()));
        }
    }

    @Override
    public ResponseResult<Object> upperLevelList(String path, String username) {
        Path currentPath = Paths.get(path);
        String upperLevel = path;
        if (currentPath.getParent() != null) {
            upperLevel = currentPath.getParent().toString();
        }
        return ResultUtil.success(listFile(upperLevel, userLoginHolder.getUserId()));
    }

    private List<FileIntroVO> listFile(String path, String userId) {
        if (!path.endsWith(File.separator)) {
            path += File.separator;
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("path").is(path));
        query.addCriteria(Criteria.where(USER_ID).is(userId));
        List<FileDocument> fileDocuments = mongoTemplate.find(query, FileDocument.class);
        return fileDocuments.stream().map(fileDocument -> {
            FileIntroVO fileIntroVO = new FileIntroVO();
            fileIntroVO.setName(fileDocument.getName());
            fileIntroVO.setSuffix(fileDocument.getSuffix());
            fileIntroVO.setContentType(fileDocument.getContentType());
            if (fileDocument.getPath().equals(File.separator)) {
                fileIntroVO.setPath(File.separator + fileDocument.getName());
            } else {
                fileIntroVO.setPath(Paths.get(fileDocument.getPath(), fileDocument.getName()).toString());
            }
            fileIntroVO.setIsFolder(fileDocument.getIsFolder());
            return fileIntroVO;
        }).sorted(this::compareByFileName).toList();
    }

    @Override
    public ResponseResult<Object> delFile(String path, String username) throws CommonException {
        Path p = Paths.get(fileProperties.getRootDir(), username, path);
        if (CommonFileService.isLock(p.toFile(), fileProperties.getRootDir(), username)) {
            throw new CommonException(ExceptionType.LOCKED_RESOURCES);
        }
        PathUtil.del(p);
        // 文件操作日志
        logService.addLogFileOperation(username, path, "彻底删除文件");
        deleteFile(username, p.toFile());
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> renameByPath(String newFileName, String username, String path) {
        Path path1 = Paths.get(fileProperties.getRootDir(), username, path);
        if (!Files.exists(path1)) {
            return ResultUtil.error("修改失败,path参数有误！");
        }
        String userId = userService.getUserIdByUserName(username);
        if (CharSequenceUtil.isBlank(userId)) {
            return ResultUtil.error("修改失败,userId参数有误！");
        }
        File file = path1.toFile();

        if (CommonFileService.isLock(file, fileProperties.getRootDir(), username)) {
            throw new CommonException(ExceptionType.LOCKED_RESOURCES);
        }

        String fileAbsolutePath = file.getAbsolutePath();
        String fileName = file.getName();
        String relativePath = fileAbsolutePath.substring(fileProperties.getRootDir().length() + username.length() + 1, fileAbsolutePath.length() - fileName.length());
        FileDocument fileDocument = getFileDocument(userId, fileName, relativePath);
        if (fileDocument == null) {
            return ResultUtil.error("修改失败！");
        }
        return rename(newFileName, username, fileDocument.getId(), null);
    }

    @Override
    public ResponseResult<FileIntroVO> addFile(String fileName, Boolean isFolder, String username, String parentPath, String folder) {
        List<OperationPermission> operationPermissionList = null;
        if (CharSequenceUtil.isNotBlank(folder)) {
            FileDocument fileDocument = getById(folder);
            username = userService.getUserNameById(fileDocument.getUserId());
            parentPath = fileDocument.getPath() + fileDocument.getName();
            operationPermissionList = fileDocument.getOperationPermissionList();
        }
        checkPermissionUsername(username, operationPermissionList, OperationPermission.UPLOAD);
        Path prePth = Paths.get(username, parentPath, fileName);
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            return ResultUtil.success(webOssService.addFile(ossPath, isFolder, prePth));
        }

        String userId = userService.getUserIdByUserName(username);
        if (CharSequenceUtil.isBlank(userId)) {
            return ResultUtil.error("不存在的用户");
        }
        Path path = Paths.get(fileProperties.getRootDir(), username, parentPath, fileName);
        if (Files.exists(path)) {
            return ResultUtil.warning("该文件已存在");
        }
        try {
            if (BooleanUtil.isTrue(isFolder)) {
                PathUtil.mkdir(path);
            } else {
                Files.createFile(path);
            }
            // 文件操作日志
            logService.addLogFileOperation(username, Paths.get(parentPath, fileName).toString(), "新建文件" + (BooleanUtil.isTrue(isFolder) ? "夹" : ""));
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return ResultUtil.error("新建文件失败");
        }
        String resPath = path.subpath(Paths.get(fileProperties.getRootDir(), username).getNameCount(), path.getNameCount()).toString();
        FileIntroVO fileIntroVO = new FileIntroVO();
        fileIntroVO.setName(fileName);
        fileIntroVO.setUserId(userId);
        fileIntroVO.setPath(resPath);
        fileIntroVO.setIsFolder(isFolder);
        fileIntroVO.setSuffix(MyFileUtils.extName(fileName));
        String fileId = uploadFile(username, path.toFile());
        fileIntroVO.setId(fileId);
        return ResultUtil.success(fileIntroVO);
    }

    @Override
    public String viewFile(String shareKey, String fileId, String shareToken, String operation) {
        FileDocument fileDocument = getById(fileId);
        if (fileDocument == null) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND.getCode(), ExceptionType.FILE_NOT_FIND.getMsg());
        }
        String username = userService.getUserNameById(fileDocument.getUserId());
        String relativepath = org.apache.catalina.util.URLEncoder.DEFAULT.encode(fileDocument.getPath() + fileDocument.getName(), StandardCharsets.UTF_8);
        StringBuilder sb = StrUtil.builder().append("forward:/file/").append(username).append(relativepath).append("?shareKey=").append(org.apache.catalina.util.URLEncoder.DEFAULT.encode(shareKey, StandardCharsets.UTF_8)).append("&o=").append(operation);
        if (!CharSequenceUtil.isBlank(shareToken)) {
            sb.append("&share-token=").append(shareToken);
        }
        return sb.toString();
    }

    @Override
    public String publicViewFile(String relativePath, String userId) {
        String username = userService.getUserNameById(userId);
        String userDirectory = aes.decryptStr(relativePath);
        return "forward:/file/" + username + userDirectory;
    }

    @Override
    public void deleteAllByUser(List<ConsumerDO> userList) {
        if (userList == null || userList.isEmpty()) {
            return;
        }
        userList.forEach(user -> {
            String username = user.getUsername();
            String userId = user.getId();
            PathUtil.del(Paths.get(fileProperties.getRootDir(), username));
            Query query = new Query();
            query.addCriteria(Criteria.where(USER_ID).in(userId));
            mongoTemplate.remove(query, COLLECTION_NAME);
        });
    }

    @Override
    public void setShareFile(FileDocument file, long expiresAt, ShareDO share) {
        if (file == null) {
            return;
        }
        Query query = new Query();
        if (Boolean.TRUE.equals(file.getIsFolder())) {
            // 共享文件夹及其下的所有文件
            query.addCriteria(Criteria.where(USER_ID).is(userLoginHolder.getUserId()));
            query.addCriteria(Criteria.where("path").regex("^" + ReUtil.escape(file.getPath() + file.getName())));
            // 设置共享属性
            setShareAttribute(file, expiresAt, share, query);
            // 修改文件夹下已经共享的文件
            query.addCriteria(Criteria.where(Constants.SHARE_BASE).is(true));
            Update update = new Update();
            update.unset(Constants.SHARE_BASE);
            update.set(Constants.SUB_SHARE, true);
            mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
        } else {
            query.addCriteria(Criteria.where("_id").is(file.getId()));
            // 设置共享属性
            setShareAttribute(file, expiresAt, share, query);
        }
    }

    @Override
    public void unsetShareFile(FileDocument file) {
        if (file == null) {
            return;
        }
        Query query = new Query();
        Path path = Paths.get(file.getId());
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            query.addCriteria(Criteria.where("_id").is(file.getId()));
            mongoTemplate.remove(query, COLLECTION_NAME);
            return;
        }
        if (Boolean.TRUE.equals(file.getIsFolder())) {
            // 解除共享文件夹及其下的所有文件
            query.addCriteria(Criteria.where(USER_ID).is(userLoginHolder.getUserId()));
            query.addCriteria(Criteria.where("path").regex("^" + ReUtil.escape(file.getPath() + file.getName())));
            // 解除共享属性
            unsetShareAttribute(file, query);
        } else {
            query.addCriteria(Criteria.where("_id").is(file.getId()));
            // 解除共享属性
            unsetShareAttribute(file, query);
        }
    }

    @Override
    public void setPublic(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update();
        update.set("isPublic", true);
        mongoTemplate.updateFirst(query, update, COLLECTION_NAME);
    }

    /***
     * 获取目录下的文件
     * @param username username
     * @param dirPath dirPath
     * @param tempDir tempDir
     */
    private List<FileIntroVO> listFile(String username, String dirPath, boolean tempDir) {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        File[] fileList = dir.listFiles();
        if (fileList == null) {
            return Lists.newArrayList();
        }
        return Arrays.stream(fileList).map(file -> {
            FileIntroVO fileDocument = new FileIntroVO();
            String filename = file.getName();
            String suffix = MyFileUtils.extName(filename);
            boolean isFolder = file.isDirectory();
            fileDocument.setName(filename);
            fileDocument.setIsFolder(isFolder);
            fileDocument.setSuffix(suffix);
            String contentType = FileContentTypeUtils.getContentType(suffix);
            fileDocument.setContentType(contentType);
            String path;
            Path dirPaths = Paths.get(file.getPath());
            if (tempDir) {
                path = dirPaths.subpath(Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username).getNameCount(), dirPaths.getNameCount()).toString();
            } else {
                path = dirPaths.subpath(Paths.get(fileProperties.getRootDir(), username).getNameCount(), dirPaths.getNameCount()).toString();
            }
            fileDocument.setPath(path);
            return fileDocument;
        }).sorted(this::compareByFileName).toList();
    }

    /***
     * 根据username 和 fileDocument 获取FilePath
     * @param username username
     * @param fileDocument FileDocument
     */
    private String getFilePathByFileId(String username, FileDocument fileDocument) throws CommonException {
        StringBuilder sb = new StringBuilder();
        sb.append(fileProperties.getRootDir()).append(File.separator).append(username).append(getUserDirectory(fileDocument.getPath())).append(fileDocument.getName());
        Path path = Paths.get(sb.toString());
        if (!Files.exists(path)) {
            throw new CommonException(ExceptionType.DIR_NOT_FIND);
        }

        return sb.toString();
    }

    /**
     * 根据fileDocument 获取 File
     *
     * @param fileDocument FileDocument
     */
    private File getFileByFileDocument(FileDocument fileDocument) throws CommonException {
        return getFileByFileDocument(userService.getUserNameById(fileDocument.getUserId()), fileDocument);
    }

    /**
     * 根据fileDocument 获取 File
     *
     * @param username     username
     * @param fileDocument FileDocument
     */
    private File getFileByFileDocument(String username, FileDocument fileDocument) throws CommonException {
        Path path = Paths.get(fileProperties.getRootDir(), username, fileDocument.getPath(), fileDocument.getName());
        if (!Files.exists(path)) {
            throw new CommonException(ExceptionType.DIR_NOT_FIND);
        }
        return path.toFile();
    }

    @Override
    public ResponseResult<Object> duplicate(String fileId, String newFilename) {
        Path prePth = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            // oss文件
            String to = prePth.getParent().resolve(newFilename).toString();
            String objectNameFrom = fileId.substring(ossPath.length());
            if (objectNameFrom.endsWith("/")) {
                return folderDuplicateDisallowed();
            }
            return webOssCopyFileService.copyOssToOss(ossPath, fileId, ossPath, to, false);
        }
        FileDocument fileDocument = getFileDocumentById(fileId);
        if (fileDocument == null) {
            return ResultUtil.error("文件不存在");
        }
        if (BooleanUtil.isTrue(fileDocument.getIsFolder())) {
            return folderDuplicateDisallowed();
        }
        String username = userService.getUserNameById(fileDocument.getUserId());
        String path = getRelativePath(fileDocument);
        Path fromFilePath = Paths.get(getUserDir(username), path);
        String toParentPath = Paths.get(path).getParent().toString();
        Path toFilePath = Paths.get(getUserDir(username), toParentPath, newFilename);
        // 复制文件
        PathUtil.copyFile(fromFilePath, toFilePath);
        // 文件操作日志
        logService.addLogFileOperation(username, Paths.get(toParentPath, newFilename).toString(), "创建副本, 源文件: \"" + path + "\"");
        // 保存文件信息
        uploadFile(username, toFilePath.toFile());
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> setTag(EditTagDTO editTagDTO) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(editTagDTO.getFileIds()));
        Update update = new Update();
        String userId = userLoginHolder.getUserId();
        if (editTagDTO.getRemoveTagIds() == null || editTagDTO.getRemoveTagIds().isEmpty()) {
            log.info("修改标签, fileIds: {}, tagList: {}", editTagDTO.getFileIds(), editTagDTO.getTagList());
            editTagDTO.getFileIds().forEach(fileId -> luceneService.pushCreateIndexQueue(fileId));
        } else {
            // 删除标签并修改相关文件
            deleteTgs(editTagDTO.getRemoveTagIds());
        }

        // 使用mongoTemplate.getConverter().convertToMongoType, 避免生成_class
        update.set("tags", mongoTemplate.getConverter().convertToMongoType(tagService.getTagIdsByTagDTOList(editTagDTO.getTagList(), userId)));
        update.set(Constants.UPDATE_DATE, LocalDateTime.now(TimeUntils.ZONE_ID));
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);

        // 推送消息
        pushMessage(userLoginHolder.getUsername(), tagService.list(userId), "updateTags");

        return ResultUtil.success();
    }

    /**
     * 删除标签并修改相关文件
     *
     * @param removeTagIds removeTagIds
     */
    private void deleteTgs(List<String> removeTagIds) {
        tagService.delete(removeTagIds);
        Query query = new Query();
        query.addCriteria(Criteria.where("tags.tagId").in(removeTagIds));
        List<FileDocument> fileDocumentList = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        fileDocumentList.parallelStream().forEach(fileDocument -> {
            List<Tag> tagList = fileDocument.getTags();
            tagList.removeIf(tagDTO -> removeTagIds.contains(tagDTO.getTagId()));
            Update update = new Update();
            update.set("tags", mongoTemplate.getConverter().convertToMongoType(tagList));
            mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
            luceneService.pushCreateIndexQueue(fileDocument.getId());
        });
    }

    @NotNull
    private static ResponseResult<Object> folderDuplicateDisallowed() {
        return ResultUtil.error("文件夹不支持创建副本");
    }

    /**
     * 复制文件
     *
     * @param upload UploadApiParamDTO
     * @param from   来源文件id
     * @param to     目标文件id
     */
    private ResponseResult<Object> copyOrMove(UploadApiParamDTO upload, String from, String to, boolean move, LogOperation logOperation) {
        FileDocument formFileDocument = getOriginalFileDocumentById(from);
        String fromPath = getRelativePath(formFileDocument);
        FileDocument toFileDocument = getToFileDocument(upload, to);

        if (CharSequenceUtil.isNotBlank(to) || CharSequenceUtil.isNotBlank(upload.getTargetPath())) {
            if (CharSequenceUtil.isBlank(to)) {
                to = MyWebdavServlet.PATH_DELIMITER + upload.getUsername() + upload.getTargetPath() + MyWebdavServlet.PATH_DELIMITER;
            }
            ResponseResult<Object> result = ossCopy(formFileDocument, toFileDocument, from, to, move);
            if (result != null) {
                return result;
            }
        }

        String toPath = getRelativePath(toFileDocument);

        if (formFileDocument != null) {

            String formUsername = userService.getUserNameById(formFileDocument.getUserId());
            String fromFilePath = getUserDir(formUsername) + fromPath;

            if (CommonFileService.isLock(formFileDocument)) {
                throw new CommonException(ExceptionType.LOCKED_RESOURCES);
            }
            Path pathFrom = Paths.get(formFileDocument.getPath(), formFileDocument.getName());
            if (toFileDocument != null) {
                String toUsername = userService.getUserNameById(toFileDocument.getUserId());
                if (!upload.getUsername().equals(toUsername)) {
                    checkPermissionUsername(toUsername, toFileDocument.getOperationPermissionList(), OperationPermission.UPLOAD);
                }
                String toFilePath = Paths.get(getUserDir(toUsername), toPath).toString();
                Path pathTo = Paths.get(toFileDocument.getPath(), toFileDocument.getName());
                formFileDocument.setUserId(toFileDocument.getUserId());

                if (Paths.get(toFilePath).equals(Paths.get(fromFilePath).getParent())) {
                    return ResultUtil.ignore("相同位置");
                }

                boolean isOverride = BooleanUtil.isTrue(upload.getIsOverride());
                if (move) {
                    FileUtil.move(new File(fromFilePath), new File(toFilePath), isOverride);
                } else {
                    FileUtil.copy(fromFilePath, toFilePath, isOverride);
                }
                String operation = move ? "移动" : "复制";
                // 文件操作日志
                String fromUserDesc = formUsername.equals(toUsername) ? "" : ", 源用户: \"" + formUsername + "\"";
                String filepath = Paths.get(toFileDocument.getPath(), toFileDocument.getName(), formFileDocument.getName()).toString();
                logService.addLogFileOperation(logOperation, toUsername, filepath, operation + "文件, 源文件: \"" + fromPath + "\"" + fromUserDesc);
                if (!formUsername.equals(toUsername)) {
                    LogOperation newLogOperation = logOperation.clone();
                    logService.addLogFileOperation(newLogOperation, formUsername, fromPath, "文件被" + operation + ", 目标文件: \"" + filepath + "\", 目标用户: \"" + toUsername + "\"");
                }
                // 复制成功
                pushMessageOperationFileSuccess(pathFrom.toString(), pathTo.toString(), upload.getUsername(), operation);
                return ResultUtil.success();
            }
        }
        return ResultUtil.error("复制失败");
    }

    private FileDocument getToFileDocument(UploadApiParamDTO upload, String to) {
        if (CharSequenceUtil.isBlank(to)) {
            if (CharSequenceUtil.isBlank(upload.getTargetPath())) {
                throw new CommonException(ExceptionType.WARNING.getCode(), "目标文件夹不能为空");
            }
            String username = userService.getUserNameById(upload.getUserId());
            FileDocument fileDocument = getFileDocument(username, Paths.get(fileProperties.getRootDir(), username, upload.getTargetPath()).toString());
            if (fileDocument == null) {
                if (getPreOssPath(upload, to) != null) {
                    return null;
                }
                throw new CommonException(ExceptionType.WARNING.getCode(), "目标文件夹不存在");
            }
            return fileDocument;
        } else {
            FileDocument toFileDocument;
            if (Constants.REGION_DEFAULT.equals(to)) {
                // 复制到根目录
                toFileDocument = new FileDocument();
                toFileDocument.setPath("/");
                toFileDocument.setName("");
                toFileDocument.setUserId(upload.getUserId());
            } else {
                toFileDocument = getOriginalFileDocumentById(to);
            }
            return toFileDocument;
        }
    }

    private void saveFileDocument(FileDocument fileDocument) {
        File file = getFileByFileDocument(fileDocument);
        String username = userService.getUserNameById(fileDocument.getUserId());
        uploadFile(username, file);
    }

    private ResponseResult<Object> ossCopy(FileDocument fileDocumentFrom, FileDocument fileDocumentTo, String from, String to, boolean isMove) {
        if (fileDocumentFrom != null && fileDocumentFrom.getOssFolder() != null) {
            if (isMove) {
                throw new CommonException("不能移动oss根目录");
            }
            String formUsername = userService.getUserNameById(fileDocumentFrom.getUserId());
            from = formUsername + MyWebdavServlet.PATH_DELIMITER + fileDocumentFrom.getOssFolder() + MyWebdavServlet.PATH_DELIMITER;
        }
        if (fileDocumentTo != null) {
            to = fileDocumentTo.getId();
        }
        if (fileDocumentTo != null && fileDocumentTo.getOssFolder() != null) {
            String toUsername = userService.getUserNameById(fileDocumentTo.getUserId());
            to = toUsername + MyWebdavServlet.PATH_DELIMITER + fileDocumentTo.getOssFolder() + MyWebdavServlet.PATH_DELIMITER;
        }
        Path prePathFrom = Paths.get(from);
        Path prePathTo = Paths.get(to);
        String ossPathFrom = CaffeineUtil.getOssPath(prePathFrom);
        String ossPathTo = CaffeineUtil.getOssPath(prePathTo);
        if (ossPathFrom != null) {
            if (ossPathTo != null) {
                // 从 oss 复制 到 oss
                return webOssCopyFileService.copyOssToOss(ossPathFrom, from, ossPathTo, to, isMove);
            } else {
                // 从 oss 复制到 本地存储
                return webOssCopyFileService.copyOssToLocal(ossPathFrom, from, to, isMove);
            }
        } else {
            if (ossPathTo != null) {
                // 从 本地存储 复制 到 oss
                return webOssCopyFileService.copyLocalToOss(ossPathTo, from, to, isMove);
            }
        }
        return null;
    }

    private static String replaceStart(String str, CharSequence searchStr, CharSequence replacement) {
        return replacement + str.substring(searchStr.length());
    }

    private boolean renameFileError(String newFileName, String fileId, String filePath, File file) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update();
        update.set("name", newFileName);
        update.set(Constants.SUFFIX, MyFileUtils.extName(newFileName));
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
        boolean isRename = false;
        try {
            isRename = file.renameTo(new File(filePath + newFileName));
        } finally {
            if (!isRename) {
                update.set("name", file.getName());
                update.set(Constants.SUFFIX, MyFileUtils.extName(file.getName()));
                mongoTemplate.upsert(query, update, COLLECTION_NAME);
            }
        }
        return !isRename;
    }

    /**
     * 上传文件
     *
     * @param upload UploadApiParamDTO
     * @return ResponseResult
     */
    @Override
    public ResponseResult<Object> upload(UploadApiParamDTO upload) throws IOException {
        setMountInfo(upload);

        checkPermissionUsername(upload.getUsername(), upload.getOperationPermissionList(), OperationPermission.UPLOAD);

        UploadResponse uploadResponse = new UploadResponse();

        Path prePth = Paths.get(upload.getUsername(), upload.getCurrentDirectory(), upload.getRelativePath());
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            return ResultUtil.success(webOssService.upload(ossPath, prePth, upload));
        }

        int currentChunkSize = upload.getCurrentChunkSize();
        long totalSize = upload.getTotalSize();
        String filename = upload.getFilename();
        String md5 = upload.getIdentifier();
        MultipartFile file = upload.getFile();
        //用户磁盘目录
        String userDirectoryFilePath = getUserDirectoryFilePath(upload);

        File chunkFile = Paths.get(fileProperties.getRootDir(), upload.getUsername(), userDirectoryFilePath).toFile();
        if (CommonFileService.isLock(chunkFile, fileProperties.getRootDir(), upload.getUsername())) {
            throw new CommonException(ExceptionType.LOCKED_RESOURCES);
        }

        if (currentChunkSize == totalSize) {
            // 没有分片,直接存
            // 保存文件信息
            upload.setInputStream(file.getInputStream());
            upload.setContentType(file.getContentType());
            upload.setSuffix(MyFileUtils.extName(filename));
            FileUtil.writeFromStream(file.getInputStream(), chunkFile);
            // 设置文件最后修改时间
            CommonFileService.setLastModifiedTime(chunkFile.toPath(), upload.getLastModified());
            // 文件操作日志
            logService.addLogFileOperation(upload.getUsername(), userDirectoryFilePath, "上传文件");
            uploadFile(upload.getUsername(), chunkFile);
            uploadResponse.setUpload(true);
        } else {
            // 上传分片
            multipartUpload.uploadChunkFile(upload, uploadResponse, md5, file);
        }
        return ResultUtil.success(uploadResponse);
    }

    @Override
    public ResponseResult<Object> uploadFolder(UploadApiParamDTO upload) throws CommonException {
        setMountInfo(upload);
        checkPermissionUsername(upload.getUsername(), upload.getOperationPermissionList(), OperationPermission.UPLOAD);
        Path prePth = Paths.get(upload.getUsername(), upload.getCurrentDirectory(), upload.getFolderPath(), upload.getFilename());
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            webOssService.mkdir(ossPath, prePth);
            return ResultUtil.success();
        }
        createFolder(upload);
        // 文件操作日志
        logService.addLogFileOperation(upload.getUsername(), getUserDirectoryFilePath(upload), "上传文件夹");
        return ResultUtil.success();
    }

    private void setMountInfo(UploadApiParamDTO upload) {
        if (CharSequenceUtil.isNotBlank(upload.getFolder())) {
            FileDocument document = getById(upload.getFolder());
            String userId = document.getUserId();
            upload.setUserId(userId);
            upload.setUsername(userService.getUserNameById(userId));
            upload.setOperationPermissionList(document.getOperationPermissionList());
            upload.setCurrentDirectory(document.getPath() + document.getName());
        }
    }

    @Override
    public ResponseResult<Object> newFolder(UploadApiParamDTO upload) throws CommonException {
        setMountInfo(upload);
        checkPermissionUsername(upload.getUsername(), upload.getOperationPermissionList(), OperationPermission.UPLOAD);
        Path prePth = Paths.get(upload.getUsername(), upload.getCurrentDirectory(), upload.getFilename());
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            return ResultUtil.success(webOssService.mkdir(ossPath, prePth));
        }

        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        // 新建文件夹
        String userDirectoryFilePath = getUserDirectoryFilePath(upload);
        File dir = new File(fileProperties.getRootDir() + File.separator + upload.getUsername() + userDirectoryFilePath);

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
        // 文件操作日志
        logService.addLogFileOperation(upload.getUsername(), getUserDirectoryFilePath(upload), "创建文件夹");
        return ResultUtil.success(uploadFile(upload.getUsername(), dir));
    }

    @Override
    public ResponseResult<Object> checkChunkUploaded(UploadApiParamDTO upload) throws IOException {
        setMountInfo(upload);
        return ResultUtil.success(multipartUpload.checkChunk(upload));
    }

    @Override
    public ResponseResult<Object> merge(UploadApiParamDTO upload) throws IOException {
        setMountInfo(upload);
        return ResultUtil.success(multipartUpload.mergeFile(upload));
    }

    @Override
    public ResponseResult<Object> favorite(List<String> fileIds) throws CommonException {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        Update update = new Update();
        fileIds.forEach(fileId -> checkOssPath(fileId, true));
        update.set(Constants.IS_FAVORITE, true);
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
        fileIds.forEach(fileId -> luceneService.pushCreateIndexQueue(fileId));
        return ResultUtil.success();
    }

    private void checkOssPath(String fileId, boolean favorite) {
        Path path = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            // oss 文件 或 目录

            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(fileId));
            if (!mongoTemplate.exists(query, FileDocument.class)) {
                FileDocument fileDocument = getById(fileId);
                fileDocument.setIsFavorite(true);
                saveFileDocument(fileDocument);
            }

            String objectName = fileId.substring(ossPath.length());
            String username = WebOssCommonService.getUsernameByOssPath(ossPath);
            String userId = userService.getUserIdByUserName(username);
            if (favorite) {
                // 设置 favorite 属性
                webOssService.setOssPath(userId, fileId, objectName, ossPath, false);
            } else {
                // 移除 favorite 属性
                List<FileDocument> fileDocumentList = webOssService.removeOssPathFile(userId, fileId, ossPath, true, false);
                mongoTemplate.insertAll(fileDocumentList);
            }
            return;
        }
        FileDocument fileDocument = getFileDocumentById(fileId);
        if (fileDocument != null && fileDocument.getOssFolder() != null) {
            // oss根目录
            String userId = fileDocument.getUserId();
            Path path1 = Paths.get(userService.getUserNameById(userId), fileDocument.getOssFolder());
            String ossPath1 = CaffeineUtil.getOssPath(path1);
            if (ossPath1 != null) {
                String objectName = WebOssService.getObjectName(path1, ossPath1, true);
                if (favorite) {
                    // 设置 favorite 属性
                    webOssService.setOssPath(userId, null, objectName, ossPath1, true);
                } else {
                    // 移除 favorite 属性
                    List<FileDocument> fileDocumentList = webOssService.removeOssPathFile(userId, null, ossPath1, true, false);
                    mongoTemplate.insertAll(fileDocumentList);
                }
            }
        }
    }

    @Override
    public ResponseResult<Object> unFavorite(List<String> fileIds) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        Update update = new Update();
        fileIds.forEach(fileId -> checkOssPath(fileId, false));
        update.set(Constants.IS_FAVORITE, false);
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
        fileIds.forEach(fileId -> luceneService.pushCreateIndexQueue(fileId));
        return ResultUtil.success();
    }

    /**
     * 仅删除文件基本信息, 不删除实际文件, 一般用于移动操作
     *
     * @param username username
     * @param fileIds  fileIds
     */
    public void deleteOnlyDoc(String username, String currentDirectory, List<String> fileIds, String operator) {
        username = deleteOss(username, currentDirectory, fileIds, operator);
        if (username == null) {
            return;
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        List<FileDocument> fileDocuments = mongoTemplate.findAllAndRemove(query, FileDocument.class, COLLECTION_NAME);
        for (FileDocument fileDocument : fileDocuments) {
            // 删除文件夹及其下的所有文件
            List<FileDocument> delFileDocumentList = mongoTemplate.findAllAndRemove(getAllByFolderQuery(fileDocument), FileDocument.class, COLLECTION_NAME);
            // 提取出delFileDocumentList中文件id
            List<String> delFileIds = delFileDocumentList.stream().map(FileDocument::getId).toList();
            deleteDependencies(username, delFileIds, false);
            pushMessage(username, fileDocument.getPath(), Constants.DELETE_FILE);
        }
    }

    @Override
    public ResponseResult<Object> delete(String username, String currentDirectory, List<String> fileIds, String operator, boolean sweep) {
        username = deleteOss(username, currentDirectory, fileIds, operator);
        if (username == null) {
            return ResultUtil.success();
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        List<FileDocument> fileDocuments = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        boolean isDel = false;
        for (FileDocument fileDocument : fileDocuments) {
            if (fileDocument.getOssFolder() != null) {
                throw new CommonException("不能删除oss根目录");
            }
            if (CommonFileService.isLock(fileDocument)) {
                throw new CommonException(ExceptionType.LOCKED_RESOURCES);
            }
            if (sweep) {
                String currentDirectory1 = getUserDirectory(fileDocument.getPath());
                String filePath = fileProperties.getRootDir() + File.separator + username + currentDirectory1 + fileDocument.getName();
                File file = new File(filePath);
                isDel = FileUtil.del(file);
            } else {
                if (BooleanUtil.isFalse(fileDocument.getIsFolder())) {
                    isDel = true;
                }
            }
            isDel = delFolder(username, sweep, fileDocument, isDel);
            pushMessage(username, fileDocument.getPath(), Constants.DELETE_FILE);
            deleteFileLog(isDel, username, sweep, fileDocument);
        }
        delDependencies(username, fileIds, sweep, isDel, query);
        return ResultUtil.success();
    }

    private void delDependencies(String username, List<String> fileIds, boolean sweep, boolean isDel, Query query) {
        OperationTips operationTips = OperationTips.builder().operation(sweep ? "删除" : "移动到回收站").build();
        if (isDel) {
            if (sweep) {
                mongoTemplate.remove(query, COLLECTION_NAME);
            } else {
                List<FileDocument> delFileDocumentList = mongoTemplate.findAllAndRemove(query, FileDocument.class, COLLECTION_NAME);
                // 移动到回收站
                moveToTrash(username, delFileDocumentList, false);
            }
            deleteDependencies(username, fileIds, sweep);
            operationTips.setSuccess(true);
        } else {
            operationTips.setSuccess(false);
        }
        pushMessage(username, operationTips, Constants.OPERATION_TIPS);
    }

    private boolean delFolder(String username, boolean sweep, FileDocument fileDocument, boolean isDel) {
        if (Boolean.TRUE.equals(fileDocument.getIsFolder())) {
            // 删除文件夹及其下的所有文件
            List<FileDocument> delFileDocumentList = mongoTemplate.findAllAndRemove(getAllByFolderQuery(fileDocument), FileDocument.class, COLLECTION_NAME);
            if (!sweep) {
                // 移动到回收站
                moveToTrash(username, delFileDocumentList, true);
            }
            // 提取出delFileDocumentList中文件id
            List<String> delFileIds = delFileDocumentList.stream().map(FileDocument::getId).toList();
            deleteDependencies(username, delFileIds, sweep);
            isDel = true;
        }
        return isDel;
    }

    /**
     * 删除文件日志
     *
     * @param logOperation logOperation
     * @param isDel        isDel
     * @param username     username
     * @param sweep        sweep
     * @param fileDocument fileDocument
     */
    private void deleteFileLog(LogOperation logOperation, boolean isDel, String username, boolean sweep, FileDocument fileDocument) {
        if (!isDel) {
            return;
        }
        String filepath = "";
        String folder = "文件";
        if (fileDocument != null) {
            filepath = Paths.get(fileDocument.getPath(), fileDocument.getName()).toString();
            folder = Boolean.TRUE.equals(fileDocument.getIsFolder()) ? "文件夹" : "文件";
        }
        String desc = logOperation.getOperationFun();
        if (CharSequenceUtil.isBlank(desc)) {
            desc = sweep ? "彻底删除" + folder : "移动到回收站";
        }
        logService.addLogFileOperation(logOperation, username, filepath, desc);
    }

    private void deleteFileLog(boolean isDel, String username, boolean sweep, FileDocument fileDocument) {
        deleteFileLog(logService.getLogOperation(), isDel, username, sweep, fileDocument);
    }

    private String deleteOss(String username, String currentDirectory, List<String> fileIds, String operator) {
        FileDocument doc = getById(fileIds.get(0));
        List<OperationPermission> operationPermissionList = null;
        if (doc != null) {
            username = userService.getUserNameById(doc.getUserId());
            currentDirectory = doc.getPath();
            operationPermissionList = doc.getOperationPermissionList();
        }
        checkPermissionUsername(username, operator, operationPermissionList, OperationPermission.DELETE);
        Path prePth = Paths.get(username, currentDirectory);
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            deleteDependencies(username, fileIds, true);
            webOssService.delete(ossPath, fileIds);
            return null;
        }
        return username;
    }

    @Override
    public ResponseResult<Object> restore(List<String> fileIds, String username) {
        LogOperation logOperation = logService.getLogOperation();
        logOperation.setOperationFun("从回收站还原文件");
        Single.create(emitter -> {
            restoreFile(username, fileIds, logOperation);
            OperationTips operationTips = OperationTips.builder().success(true).operation("还原").build();
            pushMessage(username, operationTips, Constants.OPERATION_TIPS);
        }).subscribeOn(Schedulers.io()).subscribe();
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> sweep(List<String> fileIds, String username) {
        LogOperation logOperation = logService.getLogOperation();
        Single.create(emitter -> {
            Query query = new Query(Criteria.where("_id").in(fileIds));
            deleteDependencies(username, fileIds, true);
            deleteTrash(username, query, logOperation);
            OperationTips operationTips = OperationTips.builder().success(true).operation("删除").build();
            pushMessage(username, operationTips, Constants.OPERATION_TIPS);
        }).subscribeOn(Schedulers.io()).subscribe();
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> isAllowDownload(List<String> fileIds) {
        if (fileIds.isEmpty()) {
            return ResultUtil.error("文件不存在");
        }
        LogOperation logOperation = logService.getLogOperation();
        Single.create(emitter -> {
            // 文件操作日志
            FileDocument fileDocument = getById(fileIds.get(0));
            if (fileDocument == null) {
                return;
            }
            String fileUsername = userService.getUserNameById(fileDocument.getUserId());
            String desc;
            if (fileIds.size() > 1) {
                desc = fileDocument.getPath() + fileDocument.getName() + ",等" + fileIds.size() + "个文件";
            } else {
                desc = fileDocument.getPath() + fileDocument.getName();
            }
            logService.addLogFileOperation(logOperation, fileUsername, desc, "下载文件");
        }).subscribeOn(Schedulers.io()).subscribe();
        return ResultUtil.success(true);
    }

    @Override
    public ResponseResult<Object> clearTrash(String username) {
        LogOperation logOperation = logService.getLogOperation();
        logOperation.setOperationFun("清空回收站");
        Single.create(emitter -> {
            Query query = new Query();
            query.fields().include("_id");
            List<Trash> trashList = mongoTemplate.findAllAndRemove(new Query(), Trash.class, TRASH_COLLECTION_NAME);
            // trashList 转为 fileIds
            List<String> fileIds = trashList.stream().map(Trash::getId).toList();
            deleteDependencies(username, fileIds, true);
            Path trashPath = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, fileProperties.getJmalcloudTrashDir());
            PathUtil.del(trashPath);
            deleteFileLog(logOperation, true, username, true, null);
            OperationTips operationTips = OperationTips.builder().success(true).operation("清空回收站").build();
            pushMessage(username, operationTips, Constants.OPERATION_TIPS);
        }).subscribeOn(Schedulers.io()).subscribe();
        return ResultUtil.success();
    }

    private void deleteTrash(String username, Query query, LogOperation logOperation) {
        List<FileDocument> fileDocumentList = mongoTemplate.findAllAndRemove(query, FileDocument.class, TRASH_COLLECTION_NAME);
        // 删除文件
        fileDocumentList.forEach(trashFileDocument -> {
            Path path = Paths.get(fileProperties.getRootDir(), username, trashFileDocument.getPath(), trashFileDocument.getName());
            PathUtil.del(path);
            Path trashFilePath = getTrashFilePath(username, trashFileDocument);
            PathUtil.del(trashFilePath);
            deleteFileLog(logOperation, true, username, true, trashFileDocument);
        });
    }

    private void moveToTrash(String username, List<FileDocument> delFileDocumentList, boolean hidden) {
        List<Trash> trashList = delFileDocumentList.parallelStream().map(fileDocument -> {
            if (!hidden) {
                doMoveFileToTrash(username, fileDocument);
            }
            return fileDocument.toTrash(hidden, true);
        }).toList();
        mongoTemplate.insert(trashList, TRASH_COLLECTION_NAME);
    }

    /**
     * 移动文件到回收站
     *
     * @param username     username
     * @param fileDocument fileDocument
     */
    private void doMoveFileToTrash(String username, FileDocument fileDocument) {
        Path sourceAbsolutePath;
        try {
            sourceAbsolutePath = getFileByFileDocument(username, fileDocument).toPath();
        } catch (CommonException e) {
            return;
        }
        Path trashFilePath = getTrashFilePath(username, fileDocument);
        PathUtil.move(sourceAbsolutePath, trashFilePath, true);
    }

    private Path getTrashFilePath(String username, FileDocument fileDocument) {
        return Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, fileProperties.getJmalcloudTrashDir(), fileDocument.getId() + fileDocument.getName());
    }

    public void restoreFile(String username, List<String> trashFileIdList, LogOperation logOperation) {
        trashFileIdList.forEach(trashFileId -> {
            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(trashFileId));
            FileDocument trashFileDocument = mongoTemplate.findAndRemove(query, FileDocument.class, TRASH_COLLECTION_NAME);
            if (trashFileDocument != null) {
                if (BooleanUtil.isTrue(trashFileDocument.getMove())) {
                    // 从回收站移动到原位置
                    Path trashFilePath = getTrashFilePath(username, trashFileDocument);
                    Path sourceFilePath = Paths.get(fileProperties.getRootDir(), username, trashFileDocument.getPath(), trashFileDocument.getName());
                    if (PathUtil.exists(sourceFilePath, true)) {
                        // 加一个时间后缀
                        String timePrefix = LocalDateTimeUtil.now().format(DateTimeFormatter.ofPattern(DatePattern.PURE_DATETIME_PATTERN));
                        sourceFilePath = Paths.get(fileProperties.getRootDir(), username, trashFileDocument.getPath(), timePrefix + "_" + trashFileDocument.getName());
                    }
                    mongoTemplate.insert(trashFileDocument, COLLECTION_NAME);
                    PathUtil.move(trashFilePath, sourceFilePath, false);
                } else {
                    // 老版本还原
                    if (BooleanUtil.isTrue(trashFileDocument.getIsFolder())) {
                        List<FileDocument> trashList1 = mongoTemplate.findAllAndRemove(getAllByFolderQuery(trashFileDocument), FileDocument.class, TRASH_COLLECTION_NAME);
                        mongoTemplate.insert(trashList1, COLLECTION_NAME);
                    } else {
                        mongoTemplate.insert(trashFileDocument, COLLECTION_NAME);
                    }
                }
                deleteFileLog(logOperation, true, username, true, trashFileDocument);
            }
        });
    }

}

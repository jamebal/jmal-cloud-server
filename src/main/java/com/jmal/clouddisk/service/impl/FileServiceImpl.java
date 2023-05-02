package com.jmal.clouddisk.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.oss.web.WebOssCommonService;
import com.jmal.clouddisk.oss.web.WebOssCopyFileService;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.video.VideoProcessService;
import com.jmal.clouddisk.util.*;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import com.mongodb.client.AggregateIterable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.bson.BsonNull;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.*;


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

    /***
     * 前端文件夹树的第一级的文件Id
     */
    private static final String FIRST_FILE_TREE_ID = "0";

    private static final AES aes = SecureUtil.aes();

    @Override
    public ResponseResult<Object> listFiles(UploadApiParamDTO upload) throws CommonException {
        ResponseResult<Object> result = ResultUtil.genResult();
        Path path = Paths.get(upload.getUsername(), upload.getCurrentDirectory());
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            return webOssService.searchFileAndOpenOssFolder(path, upload);
        }
        String currentDirectory = getUserDirectory(upload.getCurrentDirectory());
        Criteria criteria;
        String queryFileType = upload.getQueryFileType();
        if (!CharSequenceUtil.isBlank(queryFileType)) {
            criteria = switch (upload.getQueryFileType()) {
                case Constants.AUDIO -> Criteria.where(Constants.CONTENT_TYPE).regex("^" + Constants.AUDIO);
                case Constants.VIDEO -> Criteria.where(Constants.CONTENT_TYPE).regex("^" + Constants.VIDEO);
                case Constants.CONTENT_TYPE_IMAGE -> Criteria.where(Constants.CONTENT_TYPE).regex("^image");
                case "text" -> Criteria.where(Constants.SUFFIX).in(Arrays.asList(fileProperties.getSimText()));
                case "document" -> Criteria.where(Constants.SUFFIX).in(Arrays.asList(fileProperties.getDocument()));
                default -> Criteria.where("path").is(currentDirectory);
            };
        } else {
            criteria = Criteria.where("path").is(currentDirectory);
            Boolean isFolder = upload.getIsFolder();
            if (isFolder != null) {
                criteria = Criteria.where(Constants.IS_FOLDER).is(isFolder);
            }
            Boolean isFavorite = upload.getIsFavorite();
            if (isFavorite != null) {
                criteria = Criteria.where(Constants.IS_FAVORITE).is(isFavorite);
            }
        }
        List<FileIntroVO> list = getFileDocuments(upload, criteria);
        result.setData(list);
        result.setCount(getFileDocumentsCount(upload, criteria));
        return result;
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
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(upload.getUserId()));
        for (Criteria criteria : criteriaList) {
            query.addCriteria(criteria);
        }
        return mongoTemplate.count(query, COLLECTION_NAME);
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
        List<FileDocument> list = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        long now = System.currentTimeMillis();
        fileIntroVOList = list.parallelStream().map(fileDocument -> {
            LocalDateTime updateDate = fileDocument.getUpdateDate();
            long update = TimeUntils.getMilli(updateDate);
            fileDocument.setAgoTime(now - update);
            if (BooleanUtil.isTrue(fileDocument.getIsFolder())) {
                String path = fileDocument.getPath() + fileDocument.getName() + File.separator;
                long size = getFolderSize(fileDocument.getUserId(), path);
                fileDocument.setSize(size);
            }
            FileIntroVO fileIntroVO = new FileIntroVO();
            BeanUtils.copyProperties(fileDocument, fileIntroVO);
            return fileIntroVO;
        }).toList();
        return sortByFileName(upload, fileIntroVOList, order);
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
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg() + IUserService.USER_ID);
        }
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        for (Criteria criteria : criteriaList) {
            query.addCriteria(criteria);
        }
        return query;
    }

    @Override
    public ResponseResult<Object> searchFile(UploadApiParamDTO upload, String keyword) throws CommonException {
        ResponseResult<Object> result = ResultUtil.genResult();
        Criteria criteria1 = Criteria.where("name").regex(keyword, "i");
        return getCountResponseResult(upload, result, criteria1);
    }

    private ResponseResult<Object> getCountResponseResult(UploadApiParamDTO upload, ResponseResult<Object> result, Criteria... criteriaList) {
        List<FileIntroVO> list = getFileDocuments(upload, criteriaList);
        result.setData(list);
        result.setCount(getFileDocumentsCount(upload, criteriaList));
        return result;
    }

    @Override
    public ResponseResult<Object> searchFileAndOpenDir(UploadApiParamDTO upload, String id) throws CommonException {
        ResponseResult<Object> result = ResultUtil.genResult();

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
        String currentDirectory = getUserDirectory(fileDocument.getPath() + fileDocument.getName());

        // 判断是否为ossPath
        path = Paths.get(upload.getUsername(), currentDirectory);
        ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            return webOssService.searchFileAndOpenOssFolder(path, upload);
        }

        Criteria criteria = Criteria.where("path").is(currentDirectory);
        return getCountResponseResult(upload, result, criteria);
    }

    private FileDocument getFileDocumentById(String fileId) {
        if (CharSequenceUtil.isBlank(fileId) || FIRST_FILE_TREE_ID.equals(fileId)) {
            return null;
        }
        return mongoTemplate.findById(fileId, FileDocument.class, COLLECTION_NAME);
    }

    /***
     * 通过文件Id获取文件的相对路径
     * @param fileDocument FileDocument
     * @return 相对路径
     */
    private String getRelativePathByFileId(FileDocument fileDocument) {
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
        upload.setJustShowFolder(true);
        if (CharSequenceUtil.isNotBlank(fileId)) {
            Path path = Paths.get(fileId);
            String ossPath = CaffeineUtil.getOssPath(path);
            if (ossPath != null) {
                return webOssService.searchFileAndOpenOssFolder(path, upload);
            }
        }

        String currentDirectory = getUserDirectory(null);
        if (!CharSequenceUtil.isBlank(fileId)) {
            FileDocument fileDocument = mongoTemplate.findById(fileId, FileDocument.class, COLLECTION_NAME);
            assert fileDocument != null;

            if (fileDocument.getOssFolder() != null) {
                Path path = Paths.get(upload.getUsername(), fileDocument.getOssFolder());
                String ossPath = CaffeineUtil.getOssPath(path);
                if (ossPath != null) {
                    return webOssService.searchFileAndOpenOssFolder(path, upload);
                }
            }

            currentDirectory = getUserDirectory(fileDocument.getPath() + fileDocument.getName());
        }
        Criteria criteria = Criteria.where("path").is(currentDirectory);
        List<FileDocument> list = getDirDocuments(upload, criteria);
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
    private long getFolderSize(String userId, String path) {
        List<Bson> list = Arrays.asList(
                match(and(eq(IUserService.USER_ID, userId),
                        eq(Constants.IS_FOLDER, false), regex("path", "^" + ReUtil.escape(path)))),
                group(new BsonNull(), sum(Constants.TOTAL_SIZE, "$size")));
        AggregateIterable<Document> aggregate = mongoTemplate.getCollection(COLLECTION_NAME).aggregate(list);
        long totalSize = 0;
        Document doc = aggregate.first();
        if (doc != null) {
            Object object = doc.get(Constants.TOTAL_SIZE);
            if (object != null) {
                totalSize = Long.parseLong(object.toString());
            }
        }
        return totalSize;
    }

    @Override
    public Optional<FileDocument> getById(String id, String username) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            String currentDirectory = getUserDirectory(fileDocument.getPath());
            Path filepath = Paths.get(fileProperties.getRootDir(), username, currentDirectory, fileDocument.getName());
            if (Files.exists(filepath)) {
                File file = filepath.toFile();
                if (file.length() > 1024 * 1024 * 5) {
                    fileDocument.setContentText(MyFileUtils.readLines(file, 1000));
                } else {
                    fileDocument.setContentText(FileUtil.readString(file, CharsetUtil.charset(MyFileUtils.getFileEncode(file))));
                }
            }
            return Optional.of(fileDocument);
        }
        return Optional.empty();
    }

    @Override
    public FileDocument getFileDocumentByPathAndName(String path, String name, String username) {
        String userId = userService.getUserIdByUserName(username);
        if (userId == null) {
            return null;
        }
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("name").is(name));
        query.addCriteria(Criteria.where("path").is(path));
        return mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
    }

    @Override
    public ResponseResult<Object> previewTextByPath(String filePath, String username) throws CommonException {
        Path path = Paths.get(fileProperties.getRootDir(), username, filePath);
        File file = path.toFile();
        if (!file.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        FileDocument fileDocument = new FileDocument();
        if (file.length() > 1024 * 1024 * 5) {
            fileDocument.setContentText(MyFileUtils.readLines(file, 1000));
        } else {
            fileDocument.setContentText(FileUtil.readString(file, CharsetUtil.charset(MyFileUtils.getFileEncode(file))));
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
        return ResultUtil.success(fileDocument);
    }

    @Override
    public Optional<FileDocument> thumbnail(String id, String username) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument != null) {
            if (fileDocument.getContent() == null) {
                String currentDirectory = getUserDirectory(fileDocument.getPath());
                if (CharSequenceUtil.isBlank(username)) {
                    username = userService.getUserNameById(fileDocument.getUserId());
                }
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
    public Optional<FileDocument> coverOfMedia(String id, String username) throws CommonException {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        if (fileDocument == null) {
            return Optional.empty();
        }
        String contentType = fileDocument.getContentType();
        if (contentType.contains(Constants.VIDEO)) {
            // 视频文件
            String imagePath = videoProcessService.getVideoCover(username, fileDocument.getPath(), fileDocument.getName());
            if (!CharSequenceUtil.isBlank(imagePath)) {
                fileDocument.setContent(FileUtil.readBytes(imagePath));
            }
        } else {
            // 音频文件
            String base64 = Optional.of(fileDocument).map(FileDocument::getMusic).map(Music::getCoverBase64).orElse("");
            fileDocument.setContent(Base64.decode(base64));
        }
        fileDocument.setContentType("image/png");
        fileDocument.setName("cover");
        return Optional.of(fileDocument);
    }

    @Override
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
        if (CharSequenceUtil.isBlank(username)) {
            username = fileDocument.getUsername();
        }
        //响应头的设置
        response.reset();
        response.setCharacterEncoding("utf-8");
        response.setContentType("multipart/form-data");
        //设置压缩包的名字
        setDownloadName(request, response, fileDocument.getName() + ".zip");

        Path srcDir = Paths.get(fileProperties.getRootDir(), username, getUserDirectory(fileDocument.getPath()));
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIdList));
        List<FileDocument> fileDocuments = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
        // 选中的文件
        List<String> selectNameList;
        selectNameList = fileDocuments.stream().map(FileBase::getName).toList();
        List<String> finalSelectNameList = selectNameList;
        File[] excludeFiles = srcDir.toFile().listFiles(file -> !finalSelectNameList.contains(file.getName()));
        List<Path> excludeFilePathList = new ArrayList<>();
        if (excludeFiles != null) {
            for (File excludeFile : excludeFiles) {
                excludeFilePathList.add(excludeFile.toPath());
            }
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

    /***
     * 在nginx之前获取文件信息
     * @param fileIds fileIds
     * @param username username
     */
    private FileDocument getFileInfoBeforeDownload(List<String> fileIds, String username) throws CommonException {
        String fileId = fileIds.get(0);
        // 判断是否为ossPath
        Path path = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath == null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "暂不支持打包下载");
        }
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileId));
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class, COLLECTION_NAME);
        if (fileDocument == null) {
            return null;
        }
        // 判断是否为ossPath
        path = Paths.get(username, fileDocument.getPath(), fileDocument.getName());
        ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "暂不支持打包下载");
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
    public ResponseResult<Object> rename(String newFileName, String username, String id) {
        // 判断是否为ossPath
        ThreadUtil.execute(() -> {
            try {
                String ossPath = CaffeineUtil.getOssPath(Paths.get(id));
                if (ossPath != null) {
                    // oss 重命名
                    webOssService.rename(ossPath, id, newFileName);
                    return;
                }
                renameFile(newFileName, username, id);
            } catch (CommonException e) {
                pushMessageOperationFileError(username, Convert.toStr(e.getMsg(), Constants.UNKNOWN_ERROR), "重命名");
            } catch (Exception e) {
                pushMessageOperationFileError(username, Convert.toStr(e.getMessage(), Constants.UNKNOWN_ERROR), "重命名");
            }
        });
        return ResultUtil.success();
    }

    private void renameFile(String newFileName, String username, String id) {
        FileDocument fileDocument = mongoTemplate.findById(id, FileDocument.class, COLLECTION_NAME);
        Path fromPath = null;
        Path toPath = null;
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
                query.addCriteria(Criteria.where(IUserService.USER_ID).is(userLoginHolder.getUserId()));
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
                });
            }
            if (renameFile(newFileName, id, filePath, file)) {
                pushMessageOperationFileError(username, Convert.toStr("重命名失败", Constants.UNKNOWN_ERROR), "重命名");
            }
            pushMessage(username, fileDocument, "createFile");
        } else {
            pushMessageOperationFileError(username, Convert.toStr("数据库查询失败", Constants.UNKNOWN_ERROR), "重命名");
        }
        if (fromPath != null) {
            pushMessageOperationFileSuccess(fromPath.toString(), toPath.toString(), username, "重命名");
        }
    }

    @Override
    public ResponseResult<Object> move(UploadApiParamDTO upload, List<String> froms, String to) throws IOException {
        // 复制
        ThreadUtil.execute(() -> {
            try {
                // 复制成功
                getCopyResult(upload, froms, to, true);
                String currentDirectory = getOssFileCurrentDirectory(upload, froms);
                // 删除
                delete(upload.getUsername(), currentDirectory, froms);
            } catch (CommonException e) {
                pushMessageOperationFileError(upload.getUsername(), Convert.toStr(e.getMsg(), Constants.UNKNOWN_ERROR), "移动");
            } catch (Exception e) {
                pushMessageOperationFileError(upload.getUsername(), Convert.toStr(e.getMessage(), Constants.UNKNOWN_ERROR), "移动");
            }
        });
        return ResultUtil.success();
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

    private void getCopyResult(UploadApiParamDTO upload, List<String> froms, String to, boolean move) {
        String userId = userLoginHolder.getUserId();
        upload.setUserId(userId);
        for (String from : froms) {
            ResponseResult<Object> result;
            try {
                result = copy(upload, from, to, move);
            } catch (CommonException e) {
                throw e;
            } catch (Exception e) {
                throw new CommonException(ExceptionType.SYSTEM_ERROR);
            }
            if (result != null && result.getCode() != 0) {
                throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), Convert.toStr(result.getMessage(), Constants.UNKNOWN_ERROR));
            }
        }
    }

    @Override
    public ResponseResult<Object> copy(UploadApiParamDTO upload, List<String> froms, String to) throws IOException {
        // 复制
        ThreadUtil.execute(() -> {
            try {
                // 复制成功
                getCopyResult(upload, froms, to, false);
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
            return webOssService.getFileDocument(ossPath, fileId);
        }
        return null;
    }

    @Override
    public String createFile(String username, File file) {
        return createFile(username, file, null, null);
    }

    @Override
    public String updateFile(String username, File file) {
        return modifyFile(username, file);
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
        videoProcessService.deleteVideoCache(username, relativePath, fileName);
        Query query = new Query();
        // 文件是否存在
        FileDocument fileDocument = getFileDocument(userId, fileName, relativePath, query);
        if (fileDocument != null) {
            mongoTemplate.remove(query, COLLECTION_NAME);
        }
        pushMessage(username, fileDocument, "deleteFile");
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
            if (CharSequenceUtil.isBlank(destFileId)) {
                // 没有目标目录, 则预览解压到临时目录
                destDir = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, fileDocument.getName()).toString();
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
                        destDir = Paths.get(fileProperties.getRootDir(), username).toString();
                    }
                }
                isWrite = true;
            }

            CompressUtils.decompress(filePath, destDir, isWrite);
            return ResultUtil.success(listFile(username, destDir, !isWrite));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
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
            return webOssService.searchFileAndOpenOssFolder(prePth, uploadApiParamDTO);
        }
        String dirPath;
        if (tempDir) {
            dirPath = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, path).toString();
        } else {
            dirPath = Paths.get(fileProperties.getRootDir(), username, path).toString();
        }
        return ResultUtil.success(listFile(username, dirPath, tempDir));
    }

    @Override
    public ResponseResult<Object> upperLevelList(String path, String username) {
        String upperLevel = Paths.get(fileProperties.getRootDir(), username, path).getParent().toString();
        if (Paths.get(fileProperties.getRootDir()).toString().equals(upperLevel)) {
            upperLevel = Paths.get(fileProperties.getRootDir(), username).toString();
        }
        return ResultUtil.success(listFile(username, upperLevel, false));
    }

    @Override
    public ResponseResult<Object> delFile(String path, String username) throws CommonException {
        Path p = Paths.get(fileProperties.getRootDir(), username, path);
        if (CommonFileService.isLock(p.toFile(), fileProperties.getRootDir(), username)) {
            throw new CommonException(ExceptionType.LOCKED_RESOURCES);
        }
        PathUtil.del(p);
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
        return rename(newFileName, username, fileDocument.getId());
    }

    @Override
    public ResponseResult<FileIntroVO> addFile(String fileName, Boolean isFolder, String username, String parentPath) {

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
        fileIntroVO.setSuffix(FileUtil.extName(fileName));
        String fileId = createFile(username, path.toFile());
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
        StringBuilder sb = StrUtil.builder()
                .append("forward:/file/")
                .append(username)
                .append(relativepath)
                .append("?shareKey=")
                .append(org.apache.catalina.util.URLEncoder.DEFAULT.encode(shareKey, StandardCharsets.UTF_8))
                .append("&o=")
                .append(operation);
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
            query.addCriteria(Criteria.where(IUserService.USER_ID).in(userId));
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
            query.addCriteria(Criteria.where(IUserService.USER_ID).is(userLoginHolder.getUserId()));
            query.addCriteria(Criteria.where("path").regex("^" + ReUtil.escape(file.getPath() + file.getName())));
            // 设置共享属性
            setShareAttribute(file, expiresAt, share, query);
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
            query.addCriteria(Criteria.where(IUserService.USER_ID).is(userLoginHolder.getUserId()));
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

    @Override
    public List<FileDocument> getAllDocFile() {
        Query query = new Query();
        query.addCriteria(Criteria.where("html").exists(true));
        query.addCriteria(Criteria.where(Constants.RELEASE).is(true));
        query.fields().include("_id").include("name").include("html");
        return mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
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
            String suffix = FileUtil.extName(filename);
            boolean isFolder = file.isDirectory();
            fileDocument.setName(filename);
            fileDocument.setIsFolder(isFolder);
            fileDocument.setSuffix(suffix);
            fileDocument.setContentType(FileContentTypeUtils.getContentType(suffix));
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

    /***
     * 复制文件
     * @param upload UploadApiParamDTO
     * @param from 来源文件id
     * @param to 目标文件id
     */
    private ResponseResult<Object> copy(UploadApiParamDTO upload, String from, String to, boolean move) {
        FileDocument formFileDocument = getFileDocumentById(from);
        String fromPath = getRelativePathByFileId(formFileDocument);
        String fromFilePath = getUserDir(upload.getUsername()) + fromPath;
        FileDocument toFileDocument = getFileDocumentById(to);
        ResponseResult<Object> result = ossCopy(upload.getUsername(), formFileDocument, toFileDocument, from, to, move);
        if (result != null) {
            return result;
        }

        String toPath = getRelativePathByFileId(toFileDocument);
        String toFilePath = getUserDir(upload.getUsername()) + toPath;
        if (formFileDocument != null) {
            if (CommonFileService.isLock(formFileDocument)) {
                throw new CommonException(ExceptionType.LOCKED_RESOURCES);
            }
            Path pathFrom = Paths.get(formFileDocument.getPath(), formFileDocument.getName());
            Path pathTo = Paths.get("/");
            if (toFileDocument != null) {
                pathTo = Paths.get(toFileDocument.getPath(), toFileDocument.getName());
            }
            ResponseResult<Object> result1 = copyFile(formFileDocument, fromPath, fromFilePath, toPath, toFilePath);
            if (result1 != null) return result1;
            String operation = move ? "移动" : "复制";
            // 复制成功
            pushMessageOperationFileSuccess(pathFrom.toString(), pathTo.toString(), upload.getUsername(), operation);
            return ResultUtil.success();
        }
        return ResultUtil.error("复制失败");
    }

    private ResponseResult<Object> copyFile(FileDocument formFileDocument, String fromPath, String fromFilePath, String toPath, String toFilePath) {
        FileUtil.copy(fromFilePath, toFilePath, true);
        FileDocument copyFileDocument = copyFileDocument(formFileDocument, toPath);
        if (Boolean.TRUE.equals(formFileDocument.getIsFolder())) {
            // 复制文件夹
            // 复制其本身
            if (isExistsOfToCopy(copyFileDocument, toPath)) {
                return ResultUtil.warning(Constants.COPY_EXISTS_FILE);
            }
            mongoTemplate.save(copyFileDocument, COLLECTION_NAME);
            // 复制其下的子文件或目录
            Query query = new Query();
            query.addCriteria(Criteria.where(IUserService.USER_ID).is(userLoginHolder.getUserId()));
            query.addCriteria(Criteria.where("path").regex("^" + ReUtil.escape(fromPath)));
            List<FileDocument> formList = mongoTemplate.find(query, FileDocument.class, COLLECTION_NAME);
            List<FileDocument> list = new ArrayList<>();
            for (FileDocument fileDocument : formList) {
                String oldPath = fileDocument.getPath();
                String newPath = toPath + oldPath.substring(1);
                copyFileDocument(fileDocument, newPath);
                list.add(fileDocument);
            }
            formList = list;
            mongoTemplate.insert(formList, COLLECTION_NAME);
        } else {
            // 复制文件
            // 复制其本身
            if (isExistsOfToCopy(copyFileDocument, toPath)) {
                return ResultUtil.warning(Constants.COPY_EXISTS_FILE);
            }
            mongoTemplate.save(copyFileDocument, COLLECTION_NAME);
        }
        return null;
    }

    private ResponseResult<Object> ossCopy(String username, FileDocument fileDocumentFrom, FileDocument fileDocumentTo, String from, String to, boolean isMove) {
        if (fileDocumentFrom != null && fileDocumentFrom.getOssFolder() != null) {
            if (isMove) {
                throw new CommonException("不能移动oss根目录");
            }
            from = username + MyWebdavServlet.PATH_DELIMITER + fileDocumentFrom.getOssFolder() + MyWebdavServlet.PATH_DELIMITER;
        }
        if (fileDocumentTo != null && fileDocumentTo.getOssFolder() != null) {
            to = username + MyWebdavServlet.PATH_DELIMITER + fileDocumentTo.getOssFolder() + MyWebdavServlet.PATH_DELIMITER;
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

    /***
     * 复制更新数据
     * @param formFileDocument FileDocument
     * @param toPath toPath
     */
    private FileDocument copyFileDocument(FileDocument formFileDocument, String toPath) {
        formFileDocument.setId(null);
        formFileDocument.setPath(toPath);
        formFileDocument.setUpdateDate(LocalDateTime.now(TimeUntils.ZONE_ID));
        return formFileDocument;
    }

    /***
     * 目标目录是否存该文件
     * @param formFileDocument FileDocument
     * @param toPath toPath
     */
    private boolean isExistsOfToCopy(FileDocument formFileDocument, String toPath) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(formFileDocument.getUserId()));
        query.addCriteria(Criteria.where("path").is(toPath));
        query.addCriteria(Criteria.where("name").is(formFileDocument.getName()));
        return mongoTemplate.exists(query, COLLECTION_NAME);
    }

    private static String replaceStart(String str, CharSequence searchStr, CharSequence replacement) {
        return replacement + str.substring(searchStr.length());
    }

    private boolean renameFile(String newFileName, String fileId, String filePath, File file) {
        if (file.renameTo(new File(filePath + newFileName))) {
            Query query = new Query();
            query.addCriteria(Criteria.where("_id").is(fileId));
            Update update = new Update();
            update.set("name", newFileName);
            update.set(Constants.SUFFIX, FileUtil.extName(newFileName));
            update.set("updateDate", LocalDateTime.now(TimeUntils.ZONE_ID));
            mongoTemplate.upsert(query, update, COLLECTION_NAME);
        } else {
            return true;
        }
        return false;
    }

    /**
     * 上传文件
     * @param upload UploadApiParamDTO
     * @return ResponseResult
     */
    @Override
    public ResponseResult<Object> upload(UploadApiParamDTO upload) throws IOException {
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
            upload.setSuffix(FileUtil.extName(filename));
            FileUtil.writeFromStream(file.getInputStream(), chunkFile);
            createFile(upload.getUsername(), chunkFile);
            uploadResponse.setUpload(true);
        } else {
            // 上传分片
            multipartUpload.uploadChunkFile(upload, uploadResponse, md5, file);
        }
        return ResultUtil.success(uploadResponse);
    }

    @Override
    public ResponseResult<Object> uploadFolder(UploadApiParamDTO upload) throws CommonException {
        Path prePth;
        if (upload.getFolderPath() != null) {
            prePth = Paths.get(upload.getUsername(), upload.getCurrentDirectory(), upload.getFolderPath(), upload.getFilename());
        } else {
            prePth = Paths.get(upload.getUsername(), upload.getCurrentDirectory(), upload.getFilename());
        }
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            webOssService.mkdir(ossPath, prePth);
            return ResultUtil.success();
        }
        createFolder(upload);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> newFolder(UploadApiParamDTO upload) throws CommonException {

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
        return ResultUtil.success(createFile(upload.getUsername(), dir));
    }

    @Override
    public ResponseResult<Object> checkChunkUploaded(UploadApiParamDTO upload) throws IOException {
        return ResultUtil.success(multipartUpload.checkChunk(upload));
    }

    @Override
    public ResponseResult<Object> merge(UploadApiParamDTO upload) throws IOException {
        return ResultUtil.success(multipartUpload.mergeFile(upload));
    }

    @Override
    public ResponseResult<Object> favorite(List<String> fileIds) throws CommonException {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIds));
        Update update = new Update();
        fileIds.forEach(fileId -> checkOssPath(fileId,  true));
        update.set(Constants.IS_FAVORITE, true);
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
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
                mongoTemplate.save(fileDocument);
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
        fileIds.forEach(fileId -> checkOssPath(fileId,  false));
        update.set(Constants.IS_FAVORITE, false);
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> delete(String username, String currentDirectory, List<String> fileIds) {

        Path prePth = Paths.get(username, currentDirectory);
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            webOssService.delete(ossPath, fileIds);
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
            String currentDirectory1 = getUserDirectory(fileDocument.getPath());
            String filePath = fileProperties.getRootDir() + File.separator + username + currentDirectory1 + fileDocument.getName();
            File file = new File(filePath);
            isDel = FileUtil.del(file);
            if (Boolean.TRUE.equals(fileDocument.getIsFolder())) {
                // 删除文件夹及其下的所有文件
                Query query1 = new Query();
                query1.addCriteria(Criteria.where(IUserService.USER_ID).is(userLoginHolder.getUserId()));
                query1.addCriteria(Criteria.where("path").regex("^" + ReUtil.escape(fileDocument.getPath() + fileDocument.getName())));
                mongoTemplate.remove(query1, COLLECTION_NAME);
                isDel = true;
            }
            pushMessage(username, fileDocument, "deleteFile");
        }
        if (isDel) {
            mongoTemplate.remove(query, COLLECTION_NAME);
        } else {
            throw new CommonException(-1, "删除失败");
        }
        return ResultUtil.success();
    }

}

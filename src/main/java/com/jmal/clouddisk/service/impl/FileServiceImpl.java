package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.controller.rest.FileController;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.dao.IFileQueryDAO;
import com.jmal.clouddisk.dao.IShareDAO;
import com.jmal.clouddisk.dao.ITrashDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.lucene.LuceneIndexQueueEvent;
import com.jmal.clouddisk.lucene.LuceneService;
import com.jmal.clouddisk.lucene.SearchFileService;
import com.jmal.clouddisk.media.VideoInfo;
import com.jmal.clouddisk.media.VideoProcessService;
import com.jmal.clouddisk.model.EditTagDTO;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.Music;
import com.jmal.clouddisk.model.OperationPermission;
import com.jmal.clouddisk.model.OperationTips;
import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.TagDO;
import com.jmal.clouddisk.model.TagDTO;
import com.jmal.clouddisk.model.Trash;
import com.jmal.clouddisk.model.UploadApiParamDTO;
import com.jmal.clouddisk.model.UploadResponse;
import com.jmal.clouddisk.model.file.FileBase;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileIntroVO;
import com.jmal.clouddisk.model.file.dto.FileBaseAllDTO;
import com.jmal.clouddisk.model.file.dto.FileBaseDTO;
import com.jmal.clouddisk.model.file.dto.FileBaseMountDTO;
import com.jmal.clouddisk.model.file.dto.FileBaseOssPathDTO;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.oss.IOssService;
import com.jmal.clouddisk.oss.OssConfigService;
import com.jmal.clouddisk.oss.web.WebOssCommonService;
import com.jmal.clouddisk.oss.web.WebOssCopyFileService;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.CompressUtils;
import com.jmal.clouddisk.util.FileContentTypeUtils;
import com.jmal.clouddisk.util.FileContentUtil;
import com.jmal.clouddisk.util.MyFileUtils;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TimeUntils;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.commons.compress.utils.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mozilla.universalchardet.ReaderFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


/**
 * @author jmal
 * @Description 文件管理
 * @Author jmal
 * @Date 2020-01-14 13:05
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileServiceImpl implements IFileService {

    private final IFileDAO fileDAO;

    private final IFileQueryDAO fileQueryDAO;

    private final ITrashDAO trashDAO;

    private final IShareDAO shareDAO;

    private final CommonUserService userService;

    private final UserLoginHolder userLoginHolder;

    private final LuceneService luceneService;

    private final FileProperties fileProperties;

    private final CommonFileService commonFileService;

    private final CommonUserFileService commonUserFileService;

    private final MessageService messageService;

    private final MultipartUpload multipartUpload;

    private final WebOssService webOssService;

    private final WebOssCopyFileService webOssCopyFileService;

    private final PathService pathService;

    private final VideoProcessService videoProcessService;

    private final TagService tagService;

    private final SearchFileService searchFileService;

    private static final AES aes = SecureUtil.aes();

    private final LogService logService;

    private final ApplicationEventPublisher eventPublisher;

    private Constants.UploaderOption loadStoreUploaderOption;

    @PostConstruct
    private void init() {
        loadStoreUploaderOption = new Constants.UploaderOption(Constants.LOCAL_CHUNK_SIZE, false);
    }

    @Override
    public ResponseResult<Object> listFiles(UploadApiParamDTO upload) throws CommonException {
        ResponseResult<Object> result = ResultUtil.genResult();
        Path path = Paths.get(upload.getUsername(), upload.getCurrentDirectory());
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            return webOssService.searchFileAndOpenOssFolder(path, upload);
        }
        String currentDirectory = commonFileService.getUserDirectory(upload.getCurrentDirectory());

        if (!CharSequenceUtil.isBlank(upload.getFolder()) && checkMountParam(upload, currentDirectory)) {
            return listFiles(upload);
        }

        return getFileIntroVOResult(upload, currentDirectory, result);
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
            FileDocument fileDocument = commonFileService.getById(fileId);
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
    public ResponseResult<List<FileIntroVO>> searchFile(UploadApiParamDTO upload, String keyword) throws CommonException {
        SearchDTO.SearchDTOBuilder builder = SearchDTO.builder();
        builder.keyword(keyword) // 直接在builder上调用方法，而不是在searchDTO上调用set
                .userId(userLoginHolder.getUserId())
                .currentDirectory(upload.getCurrentDirectory())
                .isFolder(upload.getIsFolder())
                .type(upload.getQueryFileType())
                .isFavorite(upload.getIsFavorite())
                .isMount(upload.getIsMount())
                .tagId(upload.getTagId())
                .folder(upload.getFolder())
                .modifyStart(upload.getQueryModifyStart())
                .modifyEnd(upload.getQueryModifyEnd())
                .sizeMin(upload.getQuerySizeMin())
                .sizeMax(upload.getQuerySizeMax())
                .searchMount(upload.getSearchMount())
                .searchOverall(upload.getSearchOverall())
                .exactSearch(upload.getExactSearch())
                .includeTagName(Convert.toBool(upload.getIncludeTagName(), true))
                .includeFileName(Convert.toBool(upload.getIncludeFileName(), true))
                .includeFileContent(Convert.toBool(upload.getIncludeFileContent(), true))
                .build();
        SearchDTO searchDTO = builder.build();
        searchDTO.setPage(upload.getPageIndex());
        searchDTO.setPageSize(upload.getPageSize());
        searchDTO.setSortProp(upload.getSortableProp());
        searchDTO.setSortOrder(upload.getOrder());
        return searchFileService.searchFile(searchDTO);
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
        FileBaseDTO fileDocument = fileDAO.findFileBaseDTOById(id);
        if (fileDocument == null) {
            return ResultUtil.error(ExceptionType.FILE_NOT_FIND);
        }
        if (!fileDocument.getUserId().equals(userLoginHolder.getUserId())) {
            Map<String, Object> props = new HashMap<>(2);
            String username = userService.getUserNameById(fileDocument.getUserId());
            props.put("fileUsername", username);
            result.setProps(props);
        }
        String currentDirectory = commonFileService.getUserDirectory(fileDocument.getPath() + fileDocument.getName());

        // 判断是否为ossPath
        path = Paths.get(upload.getUsername(), currentDirectory);
        ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            return webOssService.searchFileAndOpenOssFolder(path, upload);
        }

        upload.setQueryFileType(Constants.PATH_FIELD);
        upload.setUserId(fileDocument.getUserId());
        return getFileIntroVOResult(upload, currentDirectory, result);
    }

    private ResponseResult<Object> getFileIntroVOResult(UploadApiParamDTO upload, String currentDirectory, ResponseResult<Object> result) {
        upload.setCurrentDirectory(currentDirectory);
        Page<FileIntroVO> page = fileQueryDAO.getFileIntroVO(upload);
        messageService.pushMessage(upload.getUsername(), loadStoreUploaderOption, Constants.UPLOADER_CHUNK_SIZE);
        result.setData(page.getContent());
        result.setCount(page.getTotalElements());
        if (upload.getProps() != null) {
            result.setProps(upload.getProps());
        }
        return result;
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

    private FileDocument getOriginalFileDocumentById(String fileId) {
        FileDocument fileDocument = commonFileService.getFileDocumentById(fileId, true);
        if (fileDocument != null && fileDocument.getMountFileId() != null) {
            fileDocument = commonFileService.getFileDocumentById(fileDocument.getMountFileId(), true);
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
            return commonFileService.getUserDirectory(null);
        }
        if (Boolean.TRUE.equals(fileDocument.getIsFolder())) {
            return commonFileService.getUserDirectory(fileDocument.getPath() + fileDocument.getName());
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
        if (!CharSequenceUtil.isBlank(fileId) && !BooleanUtil.isTrue(upload.getHideMountFile())) {
            Path path = Paths.get(fileId);
            String ossPath = CaffeineUtil.getOssPath(path);
            if (ossPath != null) {
                return webOssService.searchFileAndOpenOssFolder(path, upload);
            }
        }

        String currentDirectory = commonFileService.getUserDirectory(null);
        if (!CharSequenceUtil.isBlank(fileId)) {
            FileDocument fileDocument = commonFileService.getById(fileId);
            if (fileDocument != null) {
                if (fileDocument.getOssFolder() != null && !BooleanUtil.isTrue(upload.getHideMountFile())) {
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
                currentDirectory = commonFileService.getUserDirectory(fileDocument.getPath() + fileDocument.getName());
            }
        }
        upload.setCurrentDirectory(currentDirectory);
        List<FileBaseMountDTO> list = fileQueryDAO.getDirDocuments(upload);
        // 按文件名排序
        list.sort((f1, f2) ->  FileSortService.compareByFileName(new FileBase(f1), new FileBase(f2)));
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
            if (!commonUserFileService.getDisabledWebp(userLoginHolder.getUserId()) && (!"ico".equals(FileUtil.getSuffix(newFile)))) {
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
        commonUserFileService.createFile(username, path.toFile(), userLoginHolder.getUserId(), true);
        if (path.getNameCount() > rootPathCount + 1) {
            loopCreateDir(username, rootPathCount, path.getParent());
        }
    }

    @Override
    public Optional<FileDocument> getById(String id, Boolean content) {
        FileDocument fileDocument = fileQueryDAO.findBaseFileDocumentById(id, true);
        if (fileDocument != null) {
            String currentDirectory = commonFileService.getUserDirectory(fileDocument.getPath());
            String username = userService.getUserNameById(fileDocument.getUserId());
            Path filepath = Paths.get(fileProperties.getRootDir(), username, currentDirectory, fileDocument.getName());
            if (Files.exists(filepath)) {
                File file = filepath.toFile();
                Charset charset = MyFileUtils.getCharset(file);
                if (charset != null) {
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
        FileBaseDTO fileBaseDTO = fileDAO.findFileBaseDTOById(id);
        if (fileBaseDTO != null) {
            String currentDirectory = commonFileService.getUserDirectory(fileBaseDTO.getPath());
            String username = userService.getUserNameById(fileBaseDTO.getUserId());
            Path filepath = Paths.get(fileProperties.getRootDir(), username, currentDirectory, fileBaseDTO.getName());
            if (Files.exists(filepath)) {
                File file = filepath.toFile();
                return getStreamingResponseBody(file);
            }
        }
        return _ -> {
        };
    }

    @Override
    public FileDocument getFileDocumentByPathAndName(String path, String name, String username) {
        return getFileDocumentByPathAndName(path, name, username, true);
    }

    @Override
    public FileDocument getFileDocumentByPathAndName(String path, String name, String username, boolean excludeContent) {
        String userId = userService.getUserIdByUserName(username);
        if (userId == null) {
            return null;
        }
        return commonUserFileService.getFileDocumentByPath(path, name, userId, excludeContent);
    }

    @Override
    public FileDocument previewTextByPath(String filePath, String username) throws CommonException {
        Path path = Paths.get(fileProperties.getRootDir(), username, filePath);
        File file = path.toFile();
        if (!file.exists()) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        FileDocument fileDocument = new FileDocument();
        Charset charset = MyFileUtils.getCharset(file);
        if (charset != null) {
            fileDocument.setDecoder(charset.name());
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

    private static StreamingResponseBody getStreamingResponseBody(File file) {
        // 通过扩展名判断，而不是读取文件内容
        boolean isLogFile = file.getName().toLowerCase().endsWith(".log");

        return outputStream -> {
            try (BufferedReader bufferedReader = ReaderFactory.createBufferedReader(file)) {

                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    if (isLogFile) {
                        line = Constants.ANSI_PATTERN.matcher(line).replaceAll("");
                    }
                    outputStream.write(line.getBytes(StandardCharsets.UTF_8));
                    outputStream.write(Constants.NEWLINE);
                    outputStream.flush();
                }

            } catch (ClientAbortException ignored) {
                // ignored
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        };
    }

    @Override
    public Optional<FileDocument> thumbnail(String id, Boolean showCover) {
        FileDocument fileDocument = fileQueryDAO.findBaseFileDocumentById(id, false);
        if (fileDocument != null) {

            String username = userService.getUserNameById(fileDocument.getUserId());

            if (BooleanUtil.isTrue(showCover) && BooleanUtil.isTrue(fileDocument.getShowCover())) {
                File file = FileContentUtil.getCoverPath(pathService.getVideoCacheDir(username, id));
                if (file.exists()) {
                    try {
                        fileDocument.setInputStream(new FileInputStream(file));
                    } catch (FileNotFoundException e) {
                        throw new CommonException(ExceptionType.FILE_NOT_FIND);
                    }
                    return Optional.of(fileDocument);
                }
            }
            if (fileDocument.getInputStream() == null) {
                String currentDirectory = commonFileService.getUserDirectory(fileDocument.getPath());
                File file = new File(fileProperties.getRootDir() + File.separator + username + currentDirectory + fileDocument.getName());
                if (file.exists()) {
                    try {
                        fileDocument.setInputStream(new FileInputStream(file));
                    } catch (FileNotFoundException e) {
                        throw new CommonException(ExceptionType.FILE_NOT_FIND);
                    }
                }
            } else {
                fileDocument.setContentType("image/png");
            }
            return Optional.of(fileDocument);
        }
        return Optional.empty();
    }

    @Override
    public Optional<FileDocument> getMxweb(String id) throws FileNotFoundException {
        FileDocument fileDocument = fileQueryDAO.findBaseFileDocumentById(id, true);
        if (fileDocument != null) {
            String username = userService.getUserNameById(fileDocument.getUserId());
            File file = Paths.get(pathService.getVideoCacheDir(username, id), fileDocument.getName() + Constants.MXWEB_SUFFIX).toFile();
            if (file.exists()) {
                fileDocument.setInputStream(new FileInputStream(file));
                return Optional.of(fileDocument);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<FileDocument> coverOfMedia(String id, String username) throws CommonException {
        username = userLoginHolder.getUsername();
        FileDocument fileDocument = commonFileService.getFileDocumentById(id, false);
        if (fileDocument != null && fileDocument.getInputStream() != null) {
            return Optional.of(fileDocument);
        }
        String ossPath = CaffeineUtil.getOssPath(Paths.get(id));
        if (ossPath != null) {
            // S3存储
            if (fileDocument == null) {
                FileDocument ossFileDocument = commonFileService.getFileDocumentByOssPath(ossPath, id);
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

    @Override
    public ResponseEntity<InputStreamResource> getImageInputStreamResourceEntity(FileDocument fileDocument) {
        return commonFileService.getInputStreamResourceEntity(fileDocument, fileDocument.getContentType() == null ? "image/png" : fileDocument.getContentType());
    }

    @Override
    public ResponseEntity<InputStreamResource> getInputStreamResourceEntity(FileDocument fileDocument) {
        return commonFileService.getInputStreamResourceEntity(fileDocument, fileDocument.getContentType());
    }

    private void setMediaCover(String id, String username, FileDocument fileDocument, boolean hasOldFileDocument) {
        String contentType = fileDocument.getContentType();
        if (contentType.contains(Constants.VIDEO)) {
            // 视频文件
            VideoInfo videoInfo = videoProcessService.getVideoCover(id, username, fileDocument.getPath(), fileDocument.getName());
            String imagePath = videoInfo.getCovertPath();
            if (!CharSequenceUtil.isBlank(imagePath)) {
                try {
                    fileDocument.setInputStream(new FileInputStream(imagePath));
                } catch (FileNotFoundException e) {
                    throw new CommonException(ExceptionType.FILE_NOT_FIND);
                }
                fileDocument.setContent(FileUtil.readBytes(imagePath));
                if (hasOldFileDocument) {
                    fileDAO.setContent(id, fileDocument.getContent());
                } else {
                    fileDAO.save(fileDocument);
                }
            } else {
                fileDAO.setMediaCover(id, false);
            }
            if (imagePath != null && FileUtil.exist(imagePath)) {
                FileUtil.del(imagePath);
            }
        } else {
            // 音频文件
            Optional.of(fileDocument).map(FileDocument::getMusic).map(Music::getCoverBase64).ifPresent(base64 -> {
                byte[] imageBytes = Base64.getDecoder().decode(base64);
                fileDocument.setInputStream(new ByteArrayInputStream(imageBytes));
            });
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

    public void packageDownload(HttpServletRequest request, HttpServletResponse response, List<String> fileIdList, String username) {
        String filename = getFileInfoBeforeDownload(fileIdList, username);
        if (filename == null) {
            return;
        }
        //响应头的设置
        response.reset();
        response.setCharacterEncoding("utf-8");
        response.setContentType("multipart/form-data");
        //设置压缩包的名字
        setDownloadName(request, response, filename + ".zip");

        List<FileBaseDTO> fileDocuments = fileDAO.findAllFileBaseDTOByIdIn(fileIdList);
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
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=\"" + downloadName + "\"");
    }

    /**
     * 打包下载之前要干的事
     *
     * @param fileIds  fileIds
     * @param username username
     */
    private String getFileInfoBeforeDownload(List<String> fileIds, String username) throws CommonException {
        String fileId = fileIds.getFirst();
        // 判断是否为ossPath
        Path path = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "暂不支持打包下载");
        }
        FileBaseDTO fileDocument = fileDAO.findFileBaseDTOById(fileId);
        if (fileDocument == null) {
            return null;
        }
        int size = fileIds.size();
        if (size > 0) {
            String currentDirectory = commonFileService.getUserDirectory(fileDocument.getPath());
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
            return fileDocument.getName();
        }
        return fileDocument.getName();
    }

    @Override
    public ResponseResult<Object> rename(String newFileName, String username, String id, String folder) {
        // 判断是否为ossPath
        List<OperationPermission> operationPermissionList = null;
        if (CharSequenceUtil.isNotBlank(folder)) {
            FileDocument fileDocument = commonFileService.getById(folder);
            String userId = fileDocument.getUserId();
            username = userService.getUserNameById(userId);
            operationPermissionList = fileDocument.getOperationPermissionList();
        }
        commonFileService.checkPermissionUsername(username, operationPermissionList, OperationPermission.PUT);
        String finalUsername = username;
        String operator = userLoginHolder.getUsername();
        LogOperation logOperation = logService.getLogOperation();
        Completable.fromAction(() -> {
                    try {
                        String ossPath = CaffeineUtil.getOssPath(Paths.get(id));
                        if (ossPath != null) {
                            // oss 重命名
                            webOssService.rename(ossPath, id, newFileName, operator);
                            return;
                        }
                        renameFile(newFileName, finalUsername, id, operator, logOperation);
                    } catch (CommonException e) {
                        commonFileService.pushMessageOperationFileError(finalUsername, Convert.toStr(e.getMsg(), Constants.UNKNOWN_ERROR), "重命名");
                    } catch (Exception e) {
                        commonFileService.pushMessageOperationFileError(finalUsername, Convert.toStr(e.getMessage(), Constants.UNKNOWN_ERROR), "重命名");
                    }
                }).subscribeOn(Schedulers.io())
                .doOnError(e -> log.error(e.getMessage(), e))
                .onErrorComplete()
                .subscribe();
        return ResultUtil.success();
    }

    private void renameFile(String newFileName, String username, String id, String operator, LogOperation logOperation) {
        FileBaseOssPathDTO fileDocument = fileDAO.findFileBaseOssPathDTOById(id);
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
            String currentDirectory = commonFileService.getUserDirectory(fileDocument.getPath());
            fromPath = Paths.get(currentDirectory, fileDocument.getName());
            toPath = Paths.get(currentDirectory, newFileName);
            String filePath = fileProperties.getRootDir() + File.separator + username + currentDirectory;
            File file = new File(filePath + fileDocument.getName());
            if (Boolean.TRUE.equals(fileDocument.getIsFolder())) {
                String searchPath = currentDirectory + fileDocument.getName();
                String newPath = currentDirectory + newFileName;
                List<FileBaseDTO> documentList = fileDAO.findAllByUserIdAndPathPrefix(userService.getUserIdByUserName(username), ReUtil.escape(searchPath + "/"));
                // 修改该文件夹下的所有文件的path
                documentList.parallelStream().forEach(rep -> {
                    String path = rep.getPath();
                    String newFilePath = replaceStart(path, searchPath, newPath);
                    fileDAO.setPathById(rep.getId(), newFilePath);
                    luceneService.pushCreateIndexQueue(rep.getId());
                });
            }
            if (renameFileError(newFileName, id, filePath, file)) {
                commonFileService.pushMessageOperationFileError(operator, "重命名失败", "重命名");
                return;
            }
            String oldName = fileDocument.getName();
            fileDocument.setName(newFileName);
            messageService.pushMessage(operator, fileDocument, Constants.CREATE_FILE);

            // 记录日志
            logService.syncAddLogFileOperation(logOperation, username, Paths.get(fileDocument.getPath(), fileDocument.getName()).toString(), "重命名, 从\"" + oldName + "\"到\"" + newFileName + "\"");

        } else {
            commonFileService.pushMessageOperationFileError(operator, "重命名失败", "重命名");
            return;
        }
        commonFileService.pushMessageOperationFileSuccess(fromPath.toString(), toPath.toString(), operator, "重命名");
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
        shareDAO.setFileNameByFileId(fileId, newFileName);
        // 修改关联的挂载配置
        fileDAO.setNameByMountFileId(fileId, newFileName);
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
        List<FileDocument> fileDocuments = fileDAO.findAllByUserIdAndPathAndNameIn(toFileDocument.getUserId(), toPath, fromFilenameList);
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
        return fileDAO.findFilenameListByIdIn(froms);
    }

    @Override
    public ResponseResult<Object> move(UploadApiParamDTO upload, List<String> froms, String to) throws IOException {
        // 复制
        upload.setUserId(userLoginHolder.getUserId());
        upload.setUsername(userLoginHolder.getUsername());
        LogOperation logOperation = logService.getLogOperation();
        Completable.fromAction(() -> {
                    try {
                        String currentDirectory = getOssFileCurrentDirectory(upload, froms);
                        String fromFileIdOne = froms.getFirst();
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
                            commonFileService.checkPermissionUsername(formUsername, formFileDocument.getOperationPermissionList(), OperationPermission.DELETE);
                        }
                        // 移动
                        move(upload, froms, to, currentDirectory, logOperation);
                    } catch (CommonException e) {
                        if (e.getCode() != -3) {
                            commonFileService.pushMessageOperationFileError(upload.getUsername(), Convert.toStr(e.getMsg(), Constants.UNKNOWN_ERROR), "移动");
                        }
                    } catch (Exception e) {
                        commonFileService.pushMessageOperationFileError(upload.getUsername(), Convert.toStr(e.getMessage(), Constants.UNKNOWN_ERROR), "移动");
                    }
                }).subscribeOn(Schedulers.io())
                .doOnError(e -> log.error(e.getMessage(), e))
                .onErrorComplete()
                .subscribe();
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
        String from = froms.getFirst();
        FileDocument fileDocumentFrom = commonFileService.getFileDocumentById(from, true);
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
        Completable.fromAction(() -> {
                    try {
                        // 复制成功
                        getCopyResult(upload, froms, to, false, logOperation);
                    } catch (CommonException e) {
                        commonFileService.pushMessageOperationFileError(upload.getUsername(), Convert.toStr(e.getMsg(), Constants.UNKNOWN_ERROR), "复制");
                    } catch (Exception e) {
                        commonFileService.pushMessageOperationFileError(upload.getUsername(), Convert.toStr(e.getMessage(), Constants.UNKNOWN_ERROR), "复制");
                    }
                }).subscribeOn(Schedulers.io())
                .doOnError(e -> log.error(e.getMessage(), e))
                .onErrorComplete()
                .subscribe();
        return ResultUtil.success();
    }

    @Override
    public List<String> findByIdIn(List<String> fileIdList) {
        return fileDAO.findByIdIn(fileIdList);
    }

    @Override
    public String createFile(String username, File file) {
        return commonUserFileService.createFile(username, file, null, null);
    }

    private String uploadFile(String username, File file) {
        CaffeineUtil.setUploadFileCache(file.getAbsolutePath());
        return commonUserFileService.createFile(username, file, null, null);
    }

    @Override
    public void updateFile(String username, File file) {
        commonFileService.modifyFile(username, file);
    }

    @Override
    public void deleteFile(String username, File file) {
        commonFileService.deleteFile(username, file);
    }

    @Override
    public ResponseResult<Object> unzip(String fileId, String destFileId) throws CommonException {
        try {
            String ossPath = CaffeineUtil.getOssPath(Paths.get(fileId));
            if (ossPath != null) {
                return ResultUtil.warning("暂不支持解压!");
            }
            FileDocument fileDocument = commonFileService.getById(fileId);
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
                    FileDocument dest = commonFileService.getById(destFileId);
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
                logService.asyncAddLogFileOperation(username, Paths.get(fileDocument.getPath(), fileDocument.getName()).toString(), desc);
            }
            return ResultUtil.success(listFile(username, destDir, !isWrite));
        } catch (CommonException e) {
            log.error("解压文件失败", e);
            return ResultUtil.warning(e.getMessage());
        } catch (Exception e) {
            log.error("解压文件失败", e);
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
        List<FileBaseAllDTO> fileDocuments = fileDAO.findAllFileBaseAllDTOByUserIdAndPath(userId, path);
        return fileDocuments.stream().map(fileDocument -> {
            FileIntroVO fileIntroVO = new FileIntroVO();
            fileIntroVO.setId(fileDocument.getId());
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
        }).sorted(FileSortService::compareByFileName).toList();
    }

    @Override
    public ResponseResult<Object> delFile(String path, String username) throws CommonException {
        Path p = Paths.get(fileProperties.getRootDir(), username, path);
        if (CommonFileService.isLock(p.toFile(), fileProperties.getRootDir(), username)) {
            throw new CommonException(ExceptionType.LOCKED_RESOURCES);
        }
        PathUtil.del(p);
        // 文件操作日志
        logService.asyncAddLogFileOperation(username, path, "彻底删除文件");
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
        String fileId = fileDAO.findIdByUserIdAndPathAndName(userId, relativePath, fileName);
        if (fileId == null) {
            return ResultUtil.error("修改失败！");
        }
        return rename(newFileName, username, fileId, null);
    }

    @Override
    public ResponseResult<FileIntroVO> addFile(String fileName, Boolean isFolder, String username, String parentPath, String folder) {
        List<OperationPermission> operationPermissionList = null;
        if (CharSequenceUtil.isNotBlank(folder)) {
            FileDocument fileDocument = commonFileService.getById(folder);
            username = userService.getUserNameById(fileDocument.getUserId());
            parentPath = fileDocument.getPath() + fileDocument.getName();
            operationPermissionList = fileDocument.getOperationPermissionList();
        }
        commonFileService.checkPermissionUsername(username, operationPermissionList, OperationPermission.UPLOAD);
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
        fileIntroVO.setSuffix(MyFileUtils.extName(fileName));
        String fileId = uploadFile(username, path.toFile());
        fileIntroVO.setId(fileId);
        // 文件操作日志
        logService.syncAddLogFileOperation(username, Paths.get(parentPath, fileName).toString(), "新建文件" + (BooleanUtil.isTrue(isFolder) ? "夹" : ""));
        return ResultUtil.success(fileIntroVO);
    }

    @Override
    public String viewFile(String shareKey, String fileId, String shareToken, String operation) {
        FileDocument fileDocument = commonFileService.getById(fileId);
        if (fileDocument == null) {
            throw new CommonException(ExceptionType.FILE_NOT_FIND.getCode(), ExceptionType.FILE_NOT_FIND.getMsg());
        }
        String username = userService.getUserNameById(fileDocument.getUserId());
        String relativepath = org.apache.catalina.util.URLEncoder.DEFAULT.encode(fileDocument.getPath() + fileDocument.getName(), StandardCharsets.UTF_8);
        StringBuilder sb = StrUtil.builder().append("forward:/api/file/").append(username).append(relativepath).append("?shareKey=").append(org.apache.catalina.util.URLEncoder.DEFAULT.encode(shareKey, StandardCharsets.UTF_8)).append("&o=").append(operation);
        if (!CharSequenceUtil.isBlank(shareToken)) {
            sb.append("&share-token=").append(shareToken);
        }
        return sb.toString();
    }

    @Override
    public String publicViewFile(String relativePath, String userId) {
        String username = userService.getUserNameById(userId);
        String userDirectory = aes.decryptStr(relativePath);
        return "forward:/api/file/" + username + userDirectory;
    }

    @Override
    public void setShareFile(FileDocument file, long expiresAt, ShareDO share) {
        if (file == null) {
            return;
        }
        if (Boolean.TRUE.equals(file.getIsFolder())) {
            // 共享文件夹及其下的所有文件
            // 设置共享属性
            commonFileService.setShareAttribute(file, expiresAt, share, true);
            // 修改文件夹下已经共享的文件
            fileDAO.setSubShareFormShareBase(file);
        } else {
            // 设置共享属性
            commonFileService.setShareAttribute(file, expiresAt, share, false);
        }
    }

    @Override
    public void unsetShareFile(FileDocument file) {
        if (file == null) {
            return;
        }
        Path path = Paths.get(file.getId());
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            fileDAO.removeById(file.getId());
            return;
        }
        // 解除共享属性
        commonFileService.unsetShareAttribute(file, Boolean.TRUE.equals(file.getIsFolder()));
    }

    /**
     * 获取目录下的文件
     *
     * @param username username
     * @param dirPath  dirPath
     * @param tempDir  tempDir
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
        }).sorted(FileSortService::compareByFileName).toList();
    }

    /***
     * 根据username 和 fileDocument 获取FilePath
     * @param username username
     * @param fileDocument FileDocument
     */
    private String getFilePathByFileId(String username, FileDocument fileDocument) throws CommonException {
        StringBuilder sb = new StringBuilder();
        sb.append(fileProperties.getRootDir()).append(File.separator).append(username).append(commonFileService.getUserDirectory(fileDocument.getPath())).append(fileDocument.getName());
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
    private File getFileByFileDocument(FileBaseDTO fileDocument) throws CommonException {
        return getFileByFileDocument(userService.getUserNameById(fileDocument.getUserId()), fileDocument);
    }

    /**
     * 根据fileDocument 获取 File
     *
     * @param username     username
     * @param fileDocument FileDocument
     */
    private File getFileByFileDocument(String username, FileBaseDTO fileDocument) throws CommonException {
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
        FileDocument fileDocument = commonFileService.getFileDocumentById(fileId, true);
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
        logService.syncAddLogFileOperation(username, Paths.get(toParentPath, newFilename).toString(), "创建副本, 源文件: \"" + path + "\"");
        // 保存文件信息
        uploadFile(username, toFilePath.toFile());
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> setTag(EditTagDTO editTagDTO) {
        String userId = userLoginHolder.getUserId();

        List<Tag> tagList = tagService.getTagIdsByTagDTOList(editTagDTO.getTagList(), userId);

        fileDAO.setTagsByIdIn(editTagDTO.getFileIds(), tagList);

        if (editTagDTO.getRemoveTagIds() == null || editTagDTO.getRemoveTagIds().isEmpty()) {
            log.debug("修改标签, fileIds: {}, tagList: {}", editTagDTO.getFileIds(), editTagDTO.getTagList());
            editTagDTO.getFileIds().forEach(luceneService::pushCreateIndexQueue);
        } else {
            // 删除标签并修改相关文件
            deleteTgs(editTagDTO.getRemoveTagIds());
        }

        // 推送消息
        messageService.pushMessage(userLoginHolder.getUsername(), tagService.list(userId), "updateTags");

        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> setTag(String tagId, String tagName, String color) {
        TagDO tagDO = tagService.getTagInfo(tagId);
        if (tagDO == null) {
            return ResultUtil.error("标签不存在");
        }
        TagDTO tagDTO = tagDO.toTagDTO();
        if (CharSequenceUtil.isNotBlank(tagName)) {
            tagDTO.setName(tagName);
        }
        if (CharSequenceUtil.isNotBlank(color)) {
            tagDTO.setColor(color);
        }
        tagService.update(tagDTO);
        List<TagDTO> tagList = List.of(tagDTO);
        EditTagDTO editTagDTO = new EditTagDTO();
        editTagDTO.setTagList(tagList);
        editTagDTO.setFileIds(fileDAO.getFileIdListByTagId(tagId));
        setTag(editTagDTO);
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> deleteTag(String tagId) {
        deleteTgs(List.of(tagId));
        messageService.pushMessage(userLoginHolder.getUsername(), tagService.list(userLoginHolder.getUserId()), "updateTags");
        return ResultUtil.success();
    }

    /**
     * 删除标签并修改相关文件
     *
     * @param removeTagIds removeTagIds
     */
    private void deleteTgs(List<String> removeTagIds) {
        tagService.delete(removeTagIds);
        fileDAO.removeTagsByTagIdIn(removeTagIds);
        // 修改相关文件索引
        removeTagIds.forEach(luceneService::pushCreateIndexQueue);
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

            if (CommonFileService.isLock(new FileBaseDTO(formFileDocument))) {
                throw new CommonException(ExceptionType.LOCKED_RESOURCES);
            }
            Path pathFrom = Paths.get(formFileDocument.getPath(), formFileDocument.getName());
            if (toFileDocument != null) {
                String toUsername = userService.getUserNameById(toFileDocument.getUserId());
                if (!upload.getUsername().equals(toUsername)) {
                    commonFileService.checkPermissionUsername(toUsername, toFileDocument.getOperationPermissionList(), OperationPermission.UPLOAD);
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
                if (isOverride) {
                    // 文件操作日志
                    String fromUserDesc = formUsername.equals(toUsername) ? "" : ", 源用户: \"" + formUsername + "\"";
                    String filepath = Paths.get(toFileDocument.getPath(), toFileDocument.getName(), formFileDocument.getName()).toString();
                    logService.syncAddLogFileOperation(logOperation, toUsername, filepath, operation + "文件, 源文件: \"" + fromPath + "\"" + fromUserDesc);
                    if (!formUsername.equals(toUsername)) {
                        LogOperation newLogOperation = logOperation.clone();
                        logService.syncAddLogFileOperation(newLogOperation, formUsername, fromPath, "文件被" + operation + ", 目标文件: \"" + filepath + "\", 目标用户: \"" + toUsername + "\"");
                    }
                    // 复制成功
                    commonFileService.pushMessageOperationFileSuccess(pathFrom.toString(), pathTo.toString(), upload.getUsername(), operation);
                }
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
            FileBaseDTO fileBaseDTO = commonFileService.getFileBaseDTO(username, Paths.get(fileProperties.getRootDir(), username, upload.getTargetPath()).toString());
            FileDocument fileDocument = fileDAO.findByUserIdAndPathAndName(fileBaseDTO.getUserId(), fileBaseDTO.getPath(), fileBaseDTO.getName());
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

    private void saveFileDocument(FileBaseDTO fileDocument) {
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
        if (fileDocumentTo != null && !Constants.REGION_DEFAULT.equals(to)) {
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
        fileDAO.setNameAndSuffixById(newFileName, MyFileUtils.extName(newFileName), fileId);
        boolean isRename = false;
        try {
            isRename = file.renameTo(new File(filePath + newFileName));
        } finally {
            if (!isRename) {
                String name = file.getName();
                String suffix = MyFileUtils.extName(name);
                fileDAO.setNameAndSuffixById(name, suffix, fileId);
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

        commonFileService.checkPermissionUsername(upload.getUsername(), upload.getOperationPermissionList(), OperationPermission.UPLOAD);

        UploadResponse uploadResponse = new UploadResponse();

        Path prePth = Paths.get(upload.getUsername(), upload.getCurrentDirectory(), upload.getRelativePath());
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            return ResultUtil.success(webOssService.upload(ossPath, prePth, upload));
        }

        int currentChunkSize = upload.getCurrentChunkSize();
        long totalSize = upload.getTotalSize();
        String md5 = upload.getIdentifier();
        MultipartFile file = upload.getFile();
        //用户磁盘目录
        String userDirectoryFilePath = commonUserFileService.getUserDirectoryFilePath(upload);

        File chunkFile = Paths.get(fileProperties.getRootDir(), upload.getUsername(), userDirectoryFilePath).toFile();
        if (CommonFileService.isLock(chunkFile, fileProperties.getRootDir(), upload.getUsername())) {
            throw new CommonException(ExceptionType.LOCKED_RESOURCES);
        }

        if (currentChunkSize == totalSize) {
            // 没有分片,直接存
            try (InputStream inputStream = file.getInputStream()) {
                FileUtil.writeFromStream(inputStream, chunkFile);
            }
            // 设置文件最后修改时间
            CommonUserFileService.setLastModifiedTime(chunkFile.toPath(), upload.getLastModified());
            uploadFile(upload.getUsername(), chunkFile);
            uploadResponse.setUpload(true);
            // 文件操作日志
            logService.syncAddLogFileOperation(upload.getUsername(), userDirectoryFilePath, "上传文件");
        } else {
            // 上传分片
            multipartUpload.uploadChunkFile(upload, uploadResponse, md5, file);
        }
        return ResultUtil.success(uploadResponse);
    }

    @Override
    public ResponseResult<Object> uploadFolder(UploadApiParamDTO upload) throws CommonException {
        setMountInfo(upload);
        commonFileService.checkPermissionUsername(upload.getUsername(), upload.getOperationPermissionList(), OperationPermission.UPLOAD);
        Path prePth = Paths.get(upload.getUsername(), upload.getCurrentDirectory(), upload.getFolderPath(), upload.getFilename());
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            webOssService.mkdir(ossPath, prePth);
            return ResultUtil.success();
        }
        // 判断当前目录是否存在该文件夹
        String fileId = fileDAO.findIdByUserIdAndPathAndName(upload.getUserId(), commonFileService.getUserDirectory(upload.getCurrentDirectory()), upload.getFilename());
        if (fileId != null) {
            throw new CommonException(ExceptionType.EXISTING_RESOURCES);
        }

        commonUserFileService.createFolder(upload);
        // 文件操作日志
        logService.syncAddLogFileOperation(upload.getUsername(), commonUserFileService.getUserDirectoryFilePath(upload), "上传文件夹");
        return ResultUtil.success();
    }

    private void setMountInfo(UploadApiParamDTO upload) {
        if (CharSequenceUtil.isNotBlank(upload.getFolder())) {
            FileDocument document = commonFileService.getById(upload.getFolder());
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
        commonFileService.checkPermissionUsername(upload.getUsername(), upload.getOperationPermissionList(), OperationPermission.UPLOAD);
        Path prePth = Paths.get(upload.getUsername(), upload.getCurrentDirectory(), upload.getFilename());
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            return ResultUtil.success(webOssService.mkdir(ossPath, prePth));
        }

        LocalDateTime date = LocalDateTime.now(TimeUntils.ZONE_ID);
        // 新建文件夹
        String userDirectoryFilePath = commonUserFileService.getUserDirectoryFilePath(upload);
        File dir = new File(fileProperties.getRootDir() + File.separator + upload.getUsername() + userDirectoryFilePath);

        FileDocument fileDocument = new FileDocument();
        fileDocument.setIsFolder(true);
        fileDocument.setName(upload.getFilename());

        String path = commonFileService.getUserDirectory(upload.getCurrentDirectory());
        fileDocument.setPath(path);

        fileDocument.setUserId(upload.getUserId());
        fileDocument.setUploadDate(date);
        fileDocument.setUpdateDate(date);
        if (!dir.exists()) {
            FileUtil.mkdir(dir);
        }
        // 文件操作日志
        logService.syncAddLogFileOperation(upload.getUsername(), commonUserFileService.getUserDirectoryFilePath(upload), "创建文件夹");
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
        fileIds.forEach(fileId -> checkOssPath(fileId, true));
        fileDAO.setIsFavoriteByIdIn(fileIds, true);
        fileIds.forEach(luceneService::pushCreateIndexQueue);
        return ResultUtil.success();
    }

    private void checkOssPath(String fileId, boolean favorite) {
        Path path = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(path);
        if (ossPath != null) {
            // oss 文件 或 目录

            if (fileDAO.existsById(fileId)) {
                FileBaseDTO fileDocument = fileDAO.findFileBaseDTOById(fileId);
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
                fileDAO.saveAll(fileDocumentList);
            }
            return;
        }
        FileDocument fileDocument = commonFileService.getFileDocumentById(fileId, true);
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
                    fileDAO.saveAll(fileDocumentList);
                }
            }
        }
    }

    @Override
    public ResponseResult<Object> unFavorite(List<String> fileIds) {
        fileIds.forEach(fileId -> checkOssPath(fileId, false));
        fileDAO.setIsFavoriteByIdIn(fileIds, false);
        fileIds.forEach(luceneService::pushCreateIndexQueue);
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
        List<FileBaseDTO> fileBaseDTOS = fileDAO.findAllFileBaseDTOAndRemoveByIdIn(fileIds);
        for (FileBaseDTO fileBaseDTO : fileBaseDTOS) {
            // 删除文件夹及其下的所有文件
            List<String> delFileIds = fileDAO.findAllIdsAndRemoveByFolder(fileBaseDTO);
            // 提取出delFileDocumentList中文件id
            commonFileService.deleteDependencies(username, delFileIds);
            messageService.pushMessage(username, fileBaseDTO, Constants.DELETE_FILE);
        }
    }

    @Override
    public ResponseResult<Object> delete(String username, String currentDirectory, List<String> fileIds, String operator, boolean sweep) {
        username = deleteOss(username, currentDirectory, fileIds, operator);
        if (username == null) {
            return ResultUtil.success();
        }
        List<FileBaseOssPathDTO> fileDocuments = fileDAO.findFileBaseOssPathDTOByIdIn(fileIds);
        boolean isDel = false;
        for (FileBaseOssPathDTO fileDocument : fileDocuments) {
            if (fileDocument.getOssFolder() != null) {
                throw new CommonException("不能删除oss根目录");
            }
            if (CommonFileService.isLock(fileDocument)) {
                throw new CommonException(ExceptionType.LOCKED_RESOURCES);
            }
            if (sweep) {
                String currentDirectory1 = commonFileService.getUserDirectory(fileDocument.getPath());
                String filePath = fileProperties.getRootDir() + File.separator + username + currentDirectory1 + fileDocument.getName();
                File file = new File(filePath);
                isDel = FileUtil.del(file);
            } else {
                if (!BooleanUtil.isTrue(fileDocument.getIsFolder())) {
                    isDel = true;
                }
            }
            isDel = delFolder(username, sweep, fileDocument, isDel);
            messageService.pushMessage(username, fileDocument.getPath(), Constants.DELETE_FILE);
            deleteFileLog(isDel, username, sweep, fileDocument);
        }
        delDependencies(username, fileIds, sweep, isDel);
        return ResultUtil.success();
    }

    private void delDependencies(String username, List<String> fileIds, boolean sweep, boolean isDel) {
        OperationTips operationTips = OperationTips.builder().operation(sweep ? "删除" : "移动到回收站").build();
        if (isDel) {
            if (sweep) {
                // fileDAO.removeByIdIn(fileIds);
                fileIds.forEach(fileDAO::removeById);
            } else {
                List<FileDocument> delFileDocumentList = fileDAO.findAllAndRemoveByIdIn(fileIds);
                // 移动到回收站
                moveToTrash(username, delFileDocumentList, false);
            }
            commonFileService.deleteDependencies(username, fileIds);
            operationTips.setSuccess(true);
        } else {
            operationTips.setSuccess(false);
        }
        messageService.pushMessage(username, operationTips, Constants.OPERATION_TIPS);
    }

    private boolean delFolder(String username, boolean sweep, FileBaseOssPathDTO fileBaseDTO, boolean isDel) {
        if (Boolean.TRUE.equals(fileBaseDTO.getIsFolder())) {
            // 删除文件夹及其下的所有文件
            List<String> delFileIds;
            if (!sweep) {
                // 删除文件夹及其下的所有文件
                List<FileDocument> delFileDocumentList = fileDAO.findAllAndRemoveByFolder(fileBaseDTO);
                // 提取出delFileDocumentList中文件id
                delFileIds = delFileDocumentList.stream().map(FileDocument::getId).toList();
                // 移动到回收站
                moveToTrash(username, delFileDocumentList, true);
            } else {
                delFileIds = fileDAO.findAllIdsAndRemoveByFolder(fileBaseDTO);
            }
            commonFileService.deleteDependencies(username, delFileIds);
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
    private void deleteFileLog(LogOperation logOperation, boolean isDel, String username, boolean sweep, FileBaseDTO fileDocument) {
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
        logService.asyncAddLogFileOperation(logOperation, username, filepath, desc);
    }

    private void deleteFileLog(boolean isDel, String username, boolean sweep, FileBaseOssPathDTO fileDocument) {
        deleteFileLog(logService.getLogOperation(), isDel, username, sweep, fileDocument);
    }

    private String deleteOss(String username, String currentDirectory, List<String> fileIds, String operator) {
        FileDocument doc = commonFileService.getById(fileIds.getFirst());
        List<OperationPermission> operationPermissionList = null;
        if (doc != null) {
            username = userService.getUserNameById(doc.getUserId());
            currentDirectory = doc.getPath();
            operationPermissionList = doc.getOperationPermissionList();
        }
        commonFileService.checkPermissionUsername(username, operator, operationPermissionList, OperationPermission.DELETE);
        Path prePth = Paths.get(username, currentDirectory);
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            commonFileService.deleteDependencies(username, fileIds);
            webOssService.delete(ossPath, fileIds);
            return null;
        }
        return username;
    }

    @Override
    public ResponseResult<Object> restore(List<String> fileIds, String username) {
        LogOperation logOperation = logService.getLogOperation();
        logOperation.setOperationFun("从回收站还原文件");
        Completable.fromAction(() -> {
            restoreFile(username, fileIds, logOperation);
            OperationTips operationTips = OperationTips.builder().success(true).operation("还原").build();
            messageService.pushMessage(username, operationTips, Constants.OPERATION_TIPS);
        }).subscribeOn(Schedulers.io())
                .doOnError(e -> log.error(e.getMessage(), e))
                .onErrorComplete()
                .subscribe();
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<Object> sweep(List<String> fileIds, String username) {
        LogOperation logOperation = logService.getLogOperation();
        Completable.fromAction(() -> {
            commonFileService.deleteDependencies(username, fileIds);
            deleteTrash(username, fileIds, logOperation);
            OperationTips operationTips = OperationTips.builder().success(true).operation("删除").build();
            messageService.pushMessage(username, operationTips, Constants.OPERATION_TIPS);
        }).subscribeOn(Schedulers.io())
                .doOnError(e -> log.error(e.getMessage(), e))
                .onErrorComplete()
                .subscribe();
        return ResultUtil.success();
    }

    @Override
    public ResponseResult<FileController.DownloadBeforeResult> isAllowDownload(List<String> fileIds) {
        if (fileIds.isEmpty()) {
            return ResultUtil.error("文件不存在");
        }
        String fileId = fileIds.getFirst();
        // 判断是否为ossPath
        Path prePth = Paths.get(fileId);
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            IOssService ossService = OssConfigService.getOssStorageService(ossPath);
            if (BooleanUtil.isFalse(ossService.getProxyEnabled())) {
                String presignedUrl = WebOssService.getPresignedUrl(ossPath, prePth, true);
                return ResultUtil.success(new FileController.DownloadBeforeResult(true, true, presignedUrl));
            }
        }
        LogOperation logOperation = logService.getLogOperation();
        Completable.fromAction(() -> {
            // 文件操作日志
            FileDocument fileDocument = commonFileService.getById(fileIds.getFirst());
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
            logService.asyncAddLogFileOperation(logOperation, fileUsername, desc, "下载文件");
        }).subscribeOn(Schedulers.io())
                .doOnError(e -> log.error(e.getMessage(), e))
                .onErrorComplete()
                .subscribe();
        return ResultUtil.success(new FileController.DownloadBeforeResult(true, false, null));
    }

    @Override
    public ResponseResult<FileController.DownloadBeforeResult> isAllowPackageDownload(List<String> fileIds) {
        if (fileIds.isEmpty()) {
            return ResultUtil.error("文件不存在");
        }
        for (String fileId : fileIds) {
            // 判断是否为ossPath
            Path path = Paths.get(fileId);
            String ossPath = CaffeineUtil.getOssPath(path);
            if (ossPath != null) {
                throw new CommonException(ExceptionType.WARNING.getCode(), "暂不支持打包下载");
            }
        }
        return isAllowDownload(fileIds);
    }

    @Override
    public ResponseResult<Object> clearTrash(String username) {
        LogOperation logOperation = logService.getLogOperation();
        logOperation.setOperationFun("清空回收站");
        Completable.fromAction(() -> {
            String userId = userService.getUserIdByUserName(username);
            List<String> fileIds = trashDAO.findAllIdsAndRemove(userId);
            commonFileService.deleteDependencies(username, fileIds);
            Path trashPath = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, fileProperties.getJmalcloudTrashDir());
            PathUtil.del(trashPath);
            deleteFileLog(logOperation, true, username, true, null);
            OperationTips operationTips = OperationTips.builder().success(true).operation("清空回收站").build();
            messageService.pushMessage(username, operationTips, Constants.OPERATION_TIPS);
        }).subscribeOn(Schedulers.io())
                .doOnError(e -> log.error(e.getMessage(), e))
                .onErrorComplete()
                .subscribe();
        return ResultUtil.success();
    }

    private void deleteTrash(String username, List<String> fileIds, LogOperation logOperation) {
        List<FileBaseDTO> fileDocumentList = trashDAO.findAllFileBaseDTOAndRemoveByIdIn(fileIds);
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
                doMoveFileToTrash(username, new FileBaseDTO(fileDocument));
            }
            return fileDocument.toTrash(hidden, true);
        }).toList();
        trashDAO.saveAll(trashList);
    }

    /**
     * 移动文件到回收站
     *
     * @param username     username
     * @param fileDocument fileDocument
     */
    public void doMoveFileToTrash(String username, FileBaseDTO fileDocument) {
        Path sourceAbsolutePath;
        try {
            sourceAbsolutePath = getFileByFileDocument(username, fileDocument).toPath();
        } catch (CommonException e) {
            return;
        }
        Path trashFilePath = getTrashFilePath(username, fileDocument);
        PathUtil.move(sourceAbsolutePath, trashFilePath, true);
    }

    private Path getTrashFilePath(String username, FileBaseDTO fileDocument) {
        return Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, fileProperties.getJmalcloudTrashDir(), fileDocument.getId() + fileDocument.getName());
    }

    public void restoreFile(String username, List<String> trashFileIdList, LogOperation logOperation) {
        trashFileIdList.forEach(trashFileId -> {
            FileDocument trashFileDocument = trashDAO.findAndRemoveById(trashFileId);
            if (trashFileDocument != null) {
                if (BooleanUtil.isTrue(trashFileDocument.getMove())) {
                    // 从回收站移动到原位置
                    Path trashFilePath = getTrashFilePath(username, new FileBaseDTO(trashFileDocument));
                    Path sourceFilePath = Paths.get(fileProperties.getRootDir(), username, trashFileDocument.getPath(), trashFileDocument.getName());
                    if (PathUtil.exists(sourceFilePath, true)) {
                        // 加一个时间后缀
                        String timePrefix = LocalDateTimeUtil.now().format(DateTimeFormatter.ofPattern(DatePattern.PURE_DATETIME_PATTERN));
                        sourceFilePath = Paths.get(fileProperties.getRootDir(), username, trashFileDocument.getPath(), timePrefix + "_" + trashFileDocument.getName());
                    }
                    PathUtil.move(trashFilePath, sourceFilePath, false);
                    eventPublisher.publishEvent(new LuceneIndexQueueEvent(this, trashFileDocument.getId()));
                }
                deleteFileLog(logOperation, true, username, true, new FileBaseDTO(trashFileDocument));
            }
        });
    }

}

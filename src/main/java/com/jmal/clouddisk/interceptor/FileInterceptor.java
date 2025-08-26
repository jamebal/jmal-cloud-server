package com.jmal.clouddisk.interceptor;

import cn.hutool.core.io.IoUtil;
import cn.hutool.core.net.URLDecoder;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.URLUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.media.ImageMagickProcessor;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.FileContentTypeUtils;
import com.jmal.clouddisk.util.MyFileUtils;
import com.jmal.clouddisk.util.UrlEncodingChecker;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author jmal
 * @Description 文件鉴权拦截器
 * @Date 2020-01-31 22:04
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FileInterceptor implements HandlerInterceptor {

    /***
     * 下载操作
     */
    private static final String DOWNLOAD = "download";
    /**
     * 预览
     */
    private static final String PREVIEW = "preview";
    /***
     * 剪裁图片
     */
    private static final String CROP = "crop";
    /***
     * 缩略图
     */
    private static final String THUMBNAIL = "thumbnail";
    /***
     * webp
     */
    private static final String WEBP = "webp";
    /***
     * 操作参数
     */
    private static final String OPERATION = "o";
    /***
     * fileId参数
     */
    private static final String SHARE_KEY = "shareKey";
    /***
     * 路径最小层级
     */
    private static final int MIN_COUNT = 3;

    private final FileProperties fileProperties;

    private final IFileService fileService;

    private final CommonFileService commonFileService;

    private final AuthInterceptor authInterceptor;

    private final ShareFileInterceptor shareFileInterceptor;

    private final WebOssService webOssService;

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) throws IOException, InterruptedException {
        if (internalValid(request, response)) return true;

        if (fileAuthError(request, response)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        Path path = Paths.get(request.getRequestURI());
        String filename = getDownloadFilename(request, path);
        String operation = request.getParameter(OPERATION);
        setHeader(request, response);
        if (!CharSequenceUtil.isBlank(operation)) {
            switch (operation) {
                case DOWNLOAD -> {
                    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=\"" + filename + "\"");
                    if (downloadOssFile(request, response, filename, path)) return false;
                }
                case PREVIEW -> {
                    if (previewOssFile(request, response, path)) return false;
                }
                case CROP -> handleCrop(request, response);
                case THUMBNAIL -> thumbnail(request, response);
                case WEBP -> webp(request, response);
                default -> {
                    return true;
                }
            }
        } else {
            return !previewOssFile(request, response, path);
        }
        return true;
    }

    private void previewImageFile(HttpServletRequest request, HttpServletResponse response) throws IOException {
        File file = getFileByRequest(request);
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
        responseImageFileHeader(response, file.getName());
        ImageMagickProcessor.toWebp(file, response.getOutputStream());
    }

    private void setHeader(HttpServletRequest request, HttpServletResponse response) {
        FileDocument fileDocument = getFileDocument(Paths.get(URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8)));
        if (fileDocument == null) {
            return;
        }
        String contentType = fileDocument.getContentType();
        if (CharSequenceUtil.isNotBlank(contentType) && contentType.contains("/")) {
            response.setHeader(HttpHeaders.CONTENT_TYPE, fileDocument.getContentType());
        }
        if (fileDocument.getEtag() != null) {
            response.setHeader(HttpHeaders.ETAG, fileDocument.getEtag());
        }
        setCacheControl(request, response);
    }

    /**
     * 获取下载文件名, 适配不同的浏览器
     *
     * @param request request
     * @param path    path
     * @return filename
     */
    private static String getDownloadFilename(HttpServletRequest request, Path path) {
        String filename = String.valueOf(path.getFileName());
        return getFilename(request, filename);
    }

    private static String getFilename(HttpServletRequest request, String filename) {
        if (UrlEncodingChecker.isUrlEncoded(filename)) {
            filename = URLUtil.decode(filename, StandardCharsets.UTF_8);
        }

        String gecko = "Gecko";
        String webKit = "WebKit";
        String userAgent = request.getHeader("User-Agent");
        if (userAgent.contains(gecko) || userAgent.contains(webKit)) {
            filename = new String(filename.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        } else {
            filename = URLUtil.encode(filename, StandardCharsets.UTF_8);
        }
        return filename;
    }

    public static void main(String[] args) {
        System.out.println(URLUtil.encode("未命名文件 副本.txt", StandardCharsets.UTF_8));
    }

    private boolean internalValid(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
        String requestId = (String) request.getAttribute("requestId");
        String internalToken = (String) request.getAttribute("internalToken");

        if (CharSequenceUtil.isNotBlank(requestId) && CharSequenceUtil.isNotBlank(internalToken)) {
            if (!PreFileInterceptor.isValidInternalTokenCache(requestId, internalToken)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            }
            Path path = Paths.get(request.getRequestURI());
            String filename = getDownloadFilename(request, path);
            if (downloadOssFile(request, response, filename, path)) return false;
            setHeader(request, response);
            return true;
        }
        return false;
    }

    private boolean downloadOssFile(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, String filename, Path path) {
        Path prePth = path.subpath(MIN_COUNT - 1, path.getNameCount());
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (ossPath != null) {
            webOssService.download(ossPath, prePth, request, response, filename);
            return true;
        }
        return false;
    }

    private void setCacheControl(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
        File file = getFileByRequest(request);
        if (MyFileUtils.checkNoCacheFile(file)) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=300");
        } else {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=2592000");
        }
    }

    /**
     * 预览oss文件
     */
    private boolean previewOssFile(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, Path path) throws IOException {
        Path prePth = path.subpath(MIN_COUNT - 1, path.getNameCount());
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (CharSequenceUtil.isNotBlank(ossPath)) {
            webOssService.download(ossPath, prePth, request, response, null);
            return true;
        }
        String extName = MyFileUtils.extName(prePth.getFileName().toString());
        if ("dng".equals(extName) || "heic".equals(extName) || "heif".equals(extName) || "tiff".equals(extName)) {
            previewImageFile(request, response);
            return true;
        }
        return false;
    }

    /***
     * 文件鉴权是否失败
     * 当有shareKey参数时说明是分享文件的访问
     * shareKey 代表一个分享的文件或文件夹
     * 判断当前uri所属的文件是否为该分享的文件或其子文件
     * @return true 鉴权失败，false 鉴权成功
     */
    private boolean fileAuthError(HttpServletRequest request, HttpServletResponse response) {
        String path = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, URLDecoder.decode(path, StandardCharsets.UTF_8));
        Path uriPath = Paths.get(URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8));
        String shareKey = request.getParameter(SHARE_KEY);
        if (!CharSequenceUtil.isBlank(shareKey)) {
            return validShareFile(request, uriPath, shareKey);
        } else {
            String username = authInterceptor.getUserNameByHeader(request, response);
            if (!CharSequenceUtil.isBlank(username)) {
                authInterceptor.setAuthorities(username);
            }
            int nameCount = uriPath.getNameCount();
            if (nameCount < MIN_COUNT) {
                return true;
            }
            if (nameCount == MIN_COUNT && path.startsWith("logo")) {
                return false;
            }
            if (!CharSequenceUtil.isBlank(username) && username.equals(uriPath.getName(MIN_COUNT - 1).toString())) {
                return false;
            }
            return isNotAllowAccess(getFileDocument(uriPath), request);
        }
    }

    private boolean validShareFile(HttpServletRequest request, Path uriPath, String shareKey) {
        FileDocument fileDocument = commonFileService.getById(shareKey);
        if (!isNotAllowAccess(fileDocument, request)) {
            // 判断当前uri所属的文件是否为已分享的文件或其子文件
            FileDocument thisFile = getFileDocument(uriPath);
            if (thisFile == null) {
                return true;
            }
            if (thisFile.getPath().equals(fileDocument.getPath())) {
                return false;
            }
            if (Boolean.TRUE.equals(fileDocument.getIsFolder())) {
                String parentPath = fileDocument.getPath() + fileDocument.getName();
                return !thisFile.getPath().startsWith(parentPath);
            }
        }
        return true;
    }

    /***
     * 判断文件是否不允许访问
     * 如果为公共文件，或者分享有效期内的文件，则允许访问
     * @return true 不允许访问，false 允许访问
     */
    public boolean isNotAllowAccess(FileDocument fileDocument, HttpServletRequest request) {
        if (fileDocument == null) {
            return true;
        }
        if (BooleanUtil.isTrue(fileDocument.getIsPublic())) {
            return false;
        }
        // 分享文件
        if (BooleanUtil.isTrue(fileDocument.getIsShare())) {
            return shareFileInterceptor.validShareFile(fileDocument, null, request);
        }
        return true;
    }

    private void webp(HttpServletRequest request, HttpServletResponse response) throws IOException {
        File file = getFileByRequest(request);
        FileInputStream fileInputStream = new FileInputStream(file);
        responseImageFileHeader(response, file.getName());
        ImageMagickProcessor.convertToWebp(fileInputStream, response.getOutputStream());
    }

    private void thumbnail(HttpServletRequest request, HttpServletResponse response) throws FileNotFoundException {
        Path uriPath = Paths.get(URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8));
        if (uriPath.getNameCount() < MIN_COUNT) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        FileDocument fileDocument = getFileDocument(uriPath, false);
        if (fileDocument == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path relativePath = uriPath.subpath(MIN_COUNT - 1, uriPath.getNameCount());
        InputStream inputStream;
        if (fileDocument.getContent() == null) {
            File file = Paths.get(fileProperties.getRootDir(), relativePath.toString()).toFile();
            if (!file.exists() || !file.isFile() || !file.canRead()) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            inputStream = new FileInputStream(file);
        } else {
            inputStream = new ByteArrayInputStream(fileDocument.getContent());
        }
        responseImageFileHeader(response, fileDocument.getName());
        try (ServletOutputStream outputStream = response.getOutputStream()) {
            IoUtil.copy(inputStream, outputStream);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } finally {
            IoUtil.close(inputStream);
        }
    }

    private FileDocument getFileDocument(Path uriPath, boolean excludeContent) {
        String username = uriPath.getName(MIN_COUNT - 1).toString();
        if (uriPath.getNameCount() <= MIN_COUNT) {
            return null;
        }
        String path = File.separator;
        if (uriPath.getNameCount() > MIN_COUNT + 1) {
            path = File.separator + uriPath.subpath(MIN_COUNT, uriPath.getNameCount()).getParent().toString() + File.separator;
        }
        String name = uriPath.getFileName().toString();
        return fileService.getFileDocumentByPathAndName(path, name, username, excludeContent);
    }

    private FileDocument getFileDocument(Path uriPath) {
        return getFileDocument(uriPath, true);
    }

    private void handleCrop(HttpServletRequest request, HttpServletResponse response) throws IOException, InterruptedException {
        File file = getFileByRequest(request);
        String q = request.getParameter("q");
        String w = request.getParameter("w");
        String h = request.getParameter("h");
        responseImageFileHeader(response, file.getName());
        ImageMagickProcessor.cropImage(file, q, w, h, response.getOutputStream());
    }

    private File getFileByRequest(HttpServletRequest request) {
        Path uriPath = Paths.get(URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8));
        uriPath = uriPath.subpath(MIN_COUNT - 1, uriPath.getNameCount());
        return Paths.get(fileProperties.getRootDir(), uriPath.toString()).toFile();
    }

    private void responseImageFileHeader(HttpServletResponse response, String fileName) {
        if (!CharSequenceUtil.isBlank(fileName)) {
            response.setHeader(HttpHeaders.CONTENT_TYPE, FileContentTypeUtils.getContentType(MyFileUtils.extName(fileName)));
        }
        response.setHeader(HttpHeaders.CONNECTION, "close");
        response.setHeader(HttpHeaders.CONTENT_ENCODING, "utf-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=2592000");
    }

}

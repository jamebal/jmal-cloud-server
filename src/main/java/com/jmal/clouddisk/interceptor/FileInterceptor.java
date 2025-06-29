package com.jmal.clouddisk.interceptor;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.net.URLDecoder;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.URLUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.FileContentTypeUtils;
import com.jmal.clouddisk.util.MyFileUtils;
import com.jmal.clouddisk.util.UrlEncodingChecker;
import com.luciad.imageio.webp.WebPWriteParam;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.tasks.UnsupportedFormatException;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import javax.imageio.*;
import javax.imageio.stream.FileCacheImageOutputStream;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

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
    private static final int MIN_COUNT = 2;

    private final FileProperties fileProperties;

    private final IFileService fileService;

    private final AuthInterceptor authInterceptor;

    private final ShareFileInterceptor shareFileInterceptor;

    private final WebOssService webOssService;

    @Override
    public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, @NotNull Object handler) {
        if (internalValid(request, response)) return true;

        if (fileAuthError(request, response)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return false;
        }
        Path path = Paths.get(request.getRequestURI());
        String filename = getDownloadFilename(request, path);
        String operation = request.getParameter(OPERATION);

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
        setHeader(request, response);
        return true;
    }

    private void setHeader(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) {
        FileDocument fileDocument = getFileDocument(Paths.get(URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8)));
        response.setContentType(fileDocument.getContentType());
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
        Path prePth = path.subpath(1, path.getNameCount());
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
    private boolean previewOssFile(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response, Path path) {
        Path prePth = path.subpath(1, path.getNameCount());
        String ossPath = CaffeineUtil.getOssPath(prePth);
        if (CharSequenceUtil.isNotBlank(ossPath)) {
            webOssService.download(ossPath, prePth, request, response, null);
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
            if (!CharSequenceUtil.isBlank(username) && username.equals(uriPath.getName(1).toString())) {
                return false;
            }
            return isNotAllowAccess(getFileDocument(uriPath), request);
        }
    }

    private boolean validShareFile(HttpServletRequest request, Path uriPath, String shareKey) {
        FileDocument fileDocument = fileService.getById(shareKey);
        if (!isNotAllowAccess(fileDocument, request)) {
            // 判断当前uri所属的文件是否为已分享的文件或其子文件
            FileDocument thisFile = getFileDocument(uriPath);
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

    private void webp(HttpServletRequest request, HttpServletResponse response) {
        Path uriPath = Paths.get(URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8));
        uriPath = uriPath.subpath(1, uriPath.getNameCount());
        try {
            File file = Paths.get(fileProperties.getRootDir(), uriPath.toString()).toFile();
            // 从某处获取图像进行编码
            BufferedImage image = ImageIO.read(file);

            // 获取一个WebP ImageWriter实例
            ImageWriter writer = ImageIO.getImageWritersByMIMEType("image/webp").next();
            responseHeader(response, file.getName() + ".webp", new byte[1]);
            // 配置编码参数
            WebPWriteParam writeParam = new WebPWriteParam(writer.getLocale());
            writeParam.setCompressionMode(ImageWriteParam.MODE_DEFAULT);
            // 在ImageWriter上配置输出
            FileCacheImageOutputStream output = new FileCacheImageOutputStream(response.getOutputStream(), null);
            writer.setOutput(output);
            // 编码
            writer.write(null, new IIOImage(image, null, null), writeParam);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }

    }

    private void thumbnail(HttpServletRequest request, HttpServletResponse response) {
        Path uriPath = Paths.get(URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8));
        if (uriPath.getNameCount() < MIN_COUNT) {
            return;
        }
        FileDocument fileDocument = getFileDocument(uriPath, false);
        Path relativePath = uriPath.subpath(1, uriPath.getNameCount());
        if (fileDocument != null) {
            if (fileDocument.getContent() == null) {
                File file = Paths.get(fileProperties.getRootDir(), relativePath.toString()).toFile();
                if (file.exists()) {
                    fileDocument.setContent(FileUtil.readBytes(file));
                }
            }
            responseWritImage(response, fileDocument.getName(), fileDocument.getContent());
        }
    }

    private FileDocument getFileDocument(Path uriPath, boolean excludeContent) {
        String username = uriPath.getName(1).toString();
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

    private void handleCrop(HttpServletRequest request, HttpServletResponse response) {
        File file = getFileByRequest(request);
        String q = request.getParameter("q");
        String w = request.getParameter("w");
        String h = request.getParameter("h");
        byte[] img = imageCrop(file, q, w, h);
        if (img.length > 0) {
            responseWritImage(response, file.getName(), img);
        }
    }

    private @NotNull File getFileByRequest(HttpServletRequest request) {
        Path uriPath = Paths.get(URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8));
        uriPath = uriPath.subpath(1, uriPath.getNameCount());
        return Paths.get(fileProperties.getRootDir(), uriPath.toString()).toFile();
    }

    private void responseWritImage(HttpServletResponse response, String fileName, byte[] img) {
        responseHeader(response, fileName, img);
        try (ServletOutputStream outputStream = response.getOutputStream()) {
            outputStream.write(img);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private void responseHeader(HttpServletResponse response, String fileName, byte[] img) {
        if (!CharSequenceUtil.isBlank(fileName)) {
            response.setHeader(HttpHeaders.CONTENT_TYPE, FileContentTypeUtils.getContentType(MyFileUtils.extName(fileName)));
        }
        if (img != null) {
            response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(img.length));
        }
        response.setHeader(HttpHeaders.CONNECTION, "close");
        response.setHeader(HttpHeaders.CONTENT_ENCODING, "utf-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=2592000");
    }

    /**
     * 剪裁图片
     *
     * @param srcFile 源文件
     * @param q       剪裁后的质量
     * @param w       剪裁后的宽度
     * @param h       剪裁后的高度
     * @return 剪裁后的文件
     */
    public static byte[] imageCrop(File srcFile, String q, String w, String h) {
        ByteArrayOutputStream out = null;
        ImageInputStream iis = null;
        try {
            // 使用ImageInputStream来处理大文件
            iis = ImageIO.createImageInputStream(srcFile);
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return new byte[0];
            }
            ImageReader reader = readers.next();
            reader.setInput(iis, true);

            // 获取图像元数据
            BufferedImage bim = reader.read(0);
            if (bim == null) {
                return new byte[0];
            }

            int srcWidth = bim.getWidth();
            int srcHeight = bim.getHeight();

            // 处理质量参数
            double quality = parseQuality(q);
            int width = parseDimension(w);
            int height = parseDimension(h);

            Thumbnails.Builder<? extends BufferedImage> thumbnail = Thumbnails.of(bim)
                    .outputFormat("jpg") // 指定输出格式
                    .outputQuality(quality);

            if (width > 0 && srcWidth > width) {
                if (height <= 0 || srcHeight <= height) {
                    height = (int) (width / (double) srcWidth * srcHeight);
                    height = height == 0 ? width : height;
                }
                thumbnail.size(width, height);
            } else {
                // 宽高均小，指定原大小
                thumbnail.size(srcWidth, srcHeight);
            }

            out = new ByteArrayOutputStream();
            thumbnail.toOutputStream(out);
            return out.toByteArray();
        } catch (UnsupportedFormatException e) {
            log.warn(e.getMessage(), e);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    log.error("Error closing ByteArrayOutputStream", e);
                }
            }
            if (iis != null) {
                try {
                    iis.close();
                } catch (IOException e) {
                    log.error("Error closing ImageInputStream", e);
                }
            }
        }
        return new byte[0];
    }

    private static double parseQuality(String q) {
        try {
            double quality = Convert.toDouble(q, 0.8);
            return (quality >= 0 && quality <= 1) ? quality : 0.8;
        } catch (NumberFormatException e) {
            return 0.8;
        }
    }

    private static int parseDimension(String dim) {
        try {
            return Convert.toInt(dim, -1);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

}

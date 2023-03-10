package com.jmal.clouddisk.interceptor;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.net.URLDecoder;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.ShareDO;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IShareService;
import com.jmal.clouddisk.service.impl.ShareServiceImpl;
import com.jmal.clouddisk.util.FileContentTypeUtils;
import com.jmal.clouddisk.util.ResponseResult;
import com.luciad.imageio.webp.WebPWriteParam;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.tasks.UnsupportedFormatException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
public class FileInterceptor implements HandlerInterceptor {

    /***
     * 下载操作
     */
    private static final String DOWNLOAD = "download";
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

    @Autowired
    FileProperties fileProperties;

    @Autowired
    IFileService fileService;

    @Autowired
    AuthInterceptor authInterceptor;

    @Autowired
    IShareService shareService;

    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws UnsupportedEncodingException {
        if (fileAuthError(request, response)) {
            return false;
        }
        String operation = request.getParameter(OPERATION);
        if (!CharSequenceUtil.isBlank(operation)) {
            switch (operation) {
                case DOWNLOAD:
                    Path path = Paths.get(request.getRequestURI());
                    response.setHeader("Content-Disposition", "attachment; filename" + path.getFileName());
                    break;
                case CROP:
                    handleCrop(request, response);
                    break;
                case THUMBNAIL:
                    thumbnail(request, response);
                    break;
                case WEBP:
                    webp(request, response);
                    break;
                default:
                    return true;
            }
        }
        return true;
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
            FileDocument fileDocument = fileService.getById(shareKey);
            if (!isNotAllowAccess(fileDocument, request)) {
                // 判断当前uri所属的文件是否为该分享的文件或其子文件
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
        } else {
            String username = authInterceptor.getUserNameByHeader(request, response);
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

    /***
     * 判断文件是否不允许访问
     * 如果为公共文件，或者分享有效期内的文件，则允许访问
     * @return true 不允许访问，false 允许访问
     */
    public boolean isNotAllowAccess(FileDocument fileDocument, HttpServletRequest request) {
        if (fileDocument == null) {
            return true;
        }
        if (fileDocument.getIsPublic() != null && fileDocument.getIsPublic()) {
            return false;
        }
        // 分享文件
        if (fileDocument.getIsShare() != null) {
            if (System.currentTimeMillis() >= fileDocument.getExpiresAt()) {
                // 过期了
                return true;
            }
            if (fileDocument.getShareId() == null) {
                return true;
            }
            ShareDO shareDO = shareService.getShare(fileDocument.getShareId());
            if (shareDO == null) {
                return true;
            }
            if (BooleanUtil.isFalse(shareDO.getIsPrivacy())) {
                return false;
            }
            if (request == null) {
                return true;
            }
            String shareToken = request.getHeader(ShareServiceImpl.SHARE_TOKEN);
            if (CharSequenceUtil.isBlank(shareToken)) {
                shareToken = request.getParameter(ShareServiceImpl.SHARE_TOKEN);
            }
            ResponseResult<Object> result = shareService.validShare(shareToken, shareDO.getId());
            return result != null;
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
        FileDocument fileDocument = getFileDocument(uriPath);
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

    private FileDocument getFileDocument(Path uriPath) {
        String username = uriPath.getName(1).toString();
        String path = File.separator;
        if (uriPath.getNameCount() > MIN_COUNT + 1) {
            path = File.separator + uriPath.subpath(MIN_COUNT, uriPath.getNameCount()).getParent().toString() + File.separator;
        }
        String name = uriPath.getFileName().toString();
        return fileService.getFileDocumentByPathAndName(path, name, username);
    }

    private void handleCrop(HttpServletRequest request, HttpServletResponse response) {
        Path uriPath = Paths.get(URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8));
        uriPath = uriPath.subpath(1, uriPath.getNameCount());
        File file = Paths.get(fileProperties.getRootDir(), uriPath.toString()).toFile();
        String q = request.getParameter("q");
        String w = request.getParameter("w");
        String h = request.getParameter("h");
        byte[] img = imageCrop(file, q, w, h);
        if (img.length > 0) {
            responseWritImage(response, file.getName(), img);
        }
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
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "fileName=" + fileName);
        response.setHeader(HttpHeaders.CONTENT_TYPE, FileContentTypeUtils.getContentType(FileUtil.extName(fileName)));
        response.setHeader(HttpHeaders.CONNECTION, "close");
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(img.length));
        response.setHeader(HttpHeaders.CONTENT_ENCODING, "utf-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=604800");
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
    private byte[] imageCrop(File srcFile, String q, String w, String h) {
        try {
            Thumbnails.Builder<? extends File> thumbnail = Thumbnails.of(srcFile);
            //获取图片信息
            BufferedImage bim = ImageIO.read(srcFile);
            if (bim == null) {
                return new byte[0];
            }
            int srcWidth = bim.getWidth();
            int srcHeight = bim.getHeight();
            double quality = Convert.toDouble(q, 0.8);
            if (quality >= 0 && quality <= 1) {
                thumbnail.outputQuality(quality);
            }
            int width = Convert.toInt(w, -1);
            int height = Convert.toInt(h, -1);
            if (width > 0 && srcWidth > width) {
                if (height <= 0 || srcHeight <= height) {
                    height = (int) (width / (double) srcWidth * srcHeight);
                    height = height == 0 ? width : height;
                }
                thumbnail.size(width, height);
            } else {
                //宽高均小，指定原大小
                thumbnail.size(srcWidth, srcHeight);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            thumbnail.toOutputStream(out);
            return out.toByteArray();
        } catch (UnsupportedFormatException e) {
            log.warn(e.getMessage(), e);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return new byte[0];
    }

}

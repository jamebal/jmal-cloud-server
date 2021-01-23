package com.jmal.clouddisk.interceptor;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.util.FileContentTypeUtils;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.tasks.UnsupportedFormatException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.imageio.ImageIO;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author jmal
 * @Description 鉴权拦截器
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
    private static  final String THUMBNAIL = "thumbnail";

    @Autowired
    FileProperties fileProperties;

    @Autowired
    IFileService fileService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String operation = request.getParameter("o");
        if(!StringUtils.isEmpty(operation)){
            switch (operation) {
                case DOWNLOAD:
                    Path path = Paths.get(request.getRequestURI());
                    response.setHeader("Content-Disposition", "attachment; filename"+ path.getFileName());
                    break;
                case CROP:
                    handleCrop(request, response);
                    break;
                case THUMBNAIL:
                    thumbnail(request, response);
                    break;
                default:
                    return true;
            }
        }
        return true;
    }

    private void thumbnail(HttpServletRequest request, HttpServletResponse response) {
        try {
            Path uriPath = Paths.get(URLDecoder.decode(request.getRequestURI(), "UTF-8"));
            if(uriPath.getNameCount() < 2){
                return;
            }
            String username = uriPath.getName(1).toString();
            Path relativePath = uriPath.subpath(1, uriPath.getNameCount());
            String path = "/" + uriPath.subpath(2, uriPath.getNameCount()).getParent().toString() + "/";
            String name = uriPath.getFileName().toString();
            FileDocument fileDocument = fileService.getFileDocumentByPathAndName(path, name, username);
            if(fileDocument != null){
                if (fileDocument.getContent() == null) {
                    File file = Paths.get(fileProperties.getRootDir(), relativePath.toString()).toFile();
                    if (file.exists()) {
                        fileDocument.setContent(FileUtil.readBytes(file));
                    }
                }
                responseWritImage(response, fileDocument.getName(), fileDocument.getContent());
            }
        } catch (UnsupportedEncodingException ignored) {
        }
    }

    private void handleCrop(HttpServletRequest request, HttpServletResponse response) {
        Path uriPath = Paths.get(request.getRequestURI());
        uriPath = uriPath.subpath(1, uriPath.getNameCount());
        File file = Paths.get(fileProperties.getRootDir(), uriPath.toString()).toFile();
        String q = request.getParameter("q");
        String w = request.getParameter("w");
        String h = request.getParameter("h");
        byte[] img = imageCrop(file, q, w, h);
        if(img != null) {
            responseWritImage(response, file.getName(), img);
        }
    }

    private void responseWritImage(HttpServletResponse response, String fileName, byte[] img) {
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "fileName=" + fileName);
        response.setHeader(HttpHeaders.CONTENT_TYPE, FileContentTypeUtils.getContentType(FileUtil.extName(fileName)));
        response.setHeader(HttpHeaders.CONNECTION, "close");
        response.setHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(img.length));
        response.setHeader(HttpHeaders.CONTENT_ENCODING, "utf-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=604800");
        ServletOutputStream outputStream = null;
        try {
            outputStream = response.getOutputStream();
            outputStream.write(img);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } finally {
            if(outputStream != null){
                try {
                    outputStream.close();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
        }
    }

    /**
     * 剪裁图片
     * @param srcFile 源文件
     * @param q 剪裁后的质量
     * @param w 剪裁后的宽度
     * @param h 剪裁后的高度
     * @return 剪裁后的文件
     */
    private byte[] imageCrop(File srcFile, String q, String w, String h) {
        try {
            Thumbnails.Builder<? extends File> thumbnail = Thumbnails.of(srcFile);
            //获取图片信息
            BufferedImage bim = ImageIO.read(srcFile);
            int srcWidth = bim.getWidth();
            int srcHeight = bim.getHeight();
            double quality = Convert.toDouble(q, 0.8);
            if (quality >= 0 && quality <= 1) {
                thumbnail.outputQuality(quality);
            }
            int width = Convert.toInt(w, -1);
            int height = Convert.toInt(h, -1);
            if(width > 0 && srcWidth > width) {
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
        return null;
    }

}

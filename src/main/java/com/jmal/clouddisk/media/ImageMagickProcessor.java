package com.jmal.clouddisk.media;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.ObjectId;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.dao.DataSourceType;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.util.CommandUtil;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 使用 ImageMagick 命令行工具来处理图片.
 * 这个类完全不依赖 AWT, 可以安全地在 GraalVM Native Image 中使用.
 * 依赖的外部命令行工具:
 * - convert: 用于图片转换、缩放、加水印等核心操作.
 * - identify: 用于获取图片元数据 (尺寸、格式等).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ImageMagickProcessor {

    private final FileProperties fileProperties;

    private final DataSourceProperties dataSourceProperties;

    private final FilePersistenceService filePersistenceService;

    public String generateOrcTempImagePath(String username) {
        return generateTempImagePath(username);
    }

    private String generateTempImagePath(String username) {
        Path tempPath;
        if (StrUtil.isBlank(username)) {
            tempPath = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir());
        } else {
            tempPath = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username);
        }
        if (!FileUtil.exist(tempPath.toString())) {
            FileUtil.mkdir(tempPath.toString());
        }
        return Paths.get(tempPath.toString(), ObjectId.next(true) + "_temp_ocr.png").toString();
    }

    /**
     * 生成缩略图
     *
     * @param file   File
     */
    public void generateThumbnail(File file, FileDocument fileDocument) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            cropImage(file, "1", "256", "256", byteArrayOutputStream);
            if (dataSourceProperties.getType() == DataSourceType.mongodb) {
                fileDocument.setContent(byteArrayOutputStream.toByteArray());
            } else {
                filePersistenceService.persistContent(fileDocument.getId(), byteArrayOutputStream);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.error("生成缩略图失败: {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * 将输入流转换为 WebP 格式并保存到指定文件
     * @param originImageStream 原始图片的输入流
     * @param destFile 目标文件，必须是 WebP 格式
     * @throws IOException 如果转换过程中发生 I/O 错误
     */
    public static void convertToWebpFile(InputStream originImageStream, File destFile) throws IOException {
        // 命令: magick - webp:output.webp
        CommandLine commandLine = new CommandLine("magick");
        commandLine.addArgument("-");
        commandLine.addArgument("webp:" + destFile.getAbsolutePath());
        CommandUtil.execCommand(commandLine, originImageStream, null);
    }

    /**
     * 将输入流转换为 WebP 格式并输出到指定输出流
     * @param originImageStream 原始图片的输入流
     * @param outputStream 输出流，用于接收转换后的 WebP 数据
     */
    public static void convertToWebp(InputStream originImageStream, OutputStream outputStream) {
        // 命令 : magick - webp:-
        CommandLine commandLine = new CommandLine("magick");
        commandLine.addArgument("-");
        commandLine.addArgument("webp:-");
        CommandUtil.execCommand(commandLine, originImageStream, outputStream);
    }

    /**
     * 将图片转换为 WebP 格式并输出到指定的输出流
     * @param srcFile 源文件，必须是图片格式
     * @param outputStream 输出流，用于接收转换后的 WebP 数据
     */
    public static void toWebp(File srcFile, OutputStream outputStream) {
        checkFile(srcFile);
        // 构建 ImageMagick 命令行参数
        CommandLine cmdLine = new CommandLine("magick");
        cmdLine.addArgument(srcFile.getAbsolutePath(), false);
        cmdLine.addArgument("webp:-", false);
        CommandUtil.execCommand(cmdLine, null, outputStream);
    }

    /**
     * 将图片转换为 WebP 格式并替换原文件
     * @param srcFile 源文件，必须是图片格式
     * @param destFile 目标文件，必须是 WebP 格式
     * @param deleteSrc 是否删除源文件
     */
    public static void replaceWebp(File srcFile, File destFile, boolean deleteSrc) {
        log.debug("Converting image to WebP format: {} -> {}", srcFile.getAbsolutePath(), destFile.getAbsolutePath());
        // 命令: magick input.jpg output.webp
        try {
            CommandLine cmdLine = new CommandLine("magick");
            cmdLine.addArgument(srcFile.getAbsolutePath(), false);
            cmdLine.addArgument(destFile.getAbsolutePath(), false);
            CommandUtil.execCommand(cmdLine, null, null);
        } finally {
            if (deleteSrc) {
                FileUtil.del(srcFile);
            }
        }
    }

    /**
     * 使用 ImageMagick 替换原有的 imageCrop 方法。
     * 根据给定参数对图片进行缩放和质量调整，并返回 png 格式的字节数组。
     *
     * @param srcFile 源文件
     * @param q       剪裁后的质量 (字符串 "0.0" 到 "1.0")
     * @param w       剪裁后的宽度 (字符串)
     * @param h       剪裁后的高度 (字符串)
     * @param outputStream 输出流，用于接收处理后的图片数据
     */
    public static void cropImage(File srcFile, String q, String w, String h, OutputStream outputStream) throws IOException, InterruptedException {
        checkFile(srcFile);
        // 1. 获取源图片尺寸
        ImageFormat imageFormat = identifyFormat(srcFile);
        if (imageFormat == null) {
            log.error("Could not get dimensions for file, aborting crop: {}", srcFile.getAbsolutePath());
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }

        // 2. 解析输入参数
        double quality = parseQuality(q);
        int targetWidth = parseDimension(w);
        int targetHeight = parseDimension(h);

        // 3. 构建 ImageMagick 命令
        CommandLine cmdLine = buildImageMagickThumbnailCommand(srcFile, targetWidth, imageFormat.getWidth(), targetHeight, imageFormat.getHeight(), quality);
        CommandUtil.execCommand(cmdLine, null, outputStream);
    }

    private static void checkFile(File srcFile) {
        if (srcFile == null || !srcFile.exists() || !srcFile.isFile()) {
            log.error("Invalid source file: {}", srcFile);
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
    }

    /**
     * 构建 ImageMagick thumbnail 命令行参数
     * @param srcFile 源文件
     * @param targetWidth 目标宽度
     * @param srcWidth 源文件宽度
     * @param targetHeight 目标高度
     * @param srcHeight 源文件高度
     * @param quality 输出图片质量 (0.0 - 1.0)
     * @return 构建好的命令行参数数组
     */
    private static CommandLine buildImageMagickThumbnailCommand(File srcFile, int targetWidth, int srcWidth, int targetHeight, int srcHeight, double quality) {
        CommandLine cmdLine = new CommandLine("magick");
        cmdLine.addArgument(srcFile.getAbsolutePath(), false);
        // 只有当目标宽度有效且小于源宽度时，才进行缩放
        if (targetWidth > 0 && srcWidth > targetWidth) {
            cmdLine.addArgument("-thumbnail", false);
            // 如果目标高度也有效且小于源高度，则使用 WxH 边界框
            if (targetHeight > 0 && srcHeight > targetHeight) {
                cmdLine.addArgument(targetWidth + "x" + targetHeight, false);
            } else {
                // 否则 (高度未指定或不造成缩小)，仅根据宽度缩放
                cmdLine.addArgument(targetWidth + "x", false);
            }
        }
        // 添加质量和输出格式参数
        cmdLine.addArgument("-quality", false);
        // ImageMagick 质量是 0-100
        cmdLine.addArgument(String.valueOf((int) (quality * 100)), false);
        cmdLine.addArgument("png:-", false);
        return cmdLine;
    }

    /**
     * <p>获取图片尺寸</p>
     * 命令: identify -format "%w %h" image.jpg
     *
     * @param imageFile 图片文件
     * @return 一个包含 [width, height] 的数组，如果失败则返回 null
     */
    public static ImageFormat identifyFormat(File imageFile) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        CommandLine cmdLine = new CommandLine("magick");
        cmdLine.addArgument("identify", false);
        cmdLine.addArgument("-format", false);
        cmdLine.addArgument(ImageFormat.DEFAULT_FORMAT_PARAM, false);
        cmdLine.addArgument(imageFile.getAbsolutePath(), false);
        CommandUtil.execCommand(cmdLine, null, outputStream);

        String output = IoUtil.toStr(outputStream, StandardCharsets.UTF_8);

        return ImageFormat.getImageFormat(output);
    }

    @Builder
    @Getter
    public static class ImageFormat {
        public static final String DEFAULT_FORMAT_PARAM = "%m %e %w %h";
        private String format;
        private String ext;
        private Integer width;
        private Integer height;

        public static ImageFormat getImageFormat(String output) {
            int formatLength = DEFAULT_FORMAT_PARAM.split(" ").length;
            String[] formats = output.trim().split(" ");
            if (formats.length == formatLength) {
                try {
                    return ImageFormat.builder()
                            .format(formats[0])
                            .ext(formats[1])
                            .width(Convert.toInt(formats[2], 0))
                            .height(Convert.toInt(formats[3], 0))
                            .build();
                } catch (NumberFormatException e) {
                    log.error("Failed to parse dimensions from identify output: {}", output);
                    return null;
                }
            }
            return null;
        }
    }

    /**
     * 解析质量参数 (0.0 - 1.0), 默认 0.8.
     */
    private static double parseQuality(String q) {
        if (CharSequenceUtil.isBlank(q)) {
            return 0.8;
        }
        try {
            double quality = Double.parseDouble(q);
            return (quality >= 0 && quality <= 1) ? quality : 0.8;
        } catch (NumberFormatException e) {
            return 0.8;
        }
    }

    /**
     * 解析尺寸参数, 默认 -1.
     */
    private static int parseDimension(String dim) {
        if (CharSequenceUtil.isBlank(dim)) {
            return -1;
        }
        try {
            return Integer.parseInt(dim);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

}

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
        try(FileInputStream fileInputStream = new FileInputStream(file);
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            String extName = FileUtil.extName(file);
            if ("gif".equalsIgnoreCase(extName)) {
                // GIF 图片单独处理，保持动画效果
                cropImage(fileInputStream, "1", "256", "256", byteArrayOutputStream, "gif");
            } else {
                cropImage(fileInputStream, "1", "256", "256", byteArrayOutputStream, "png");
            }
            if (dataSourceProperties.getType() == DataSourceType.mongodb) {
                fileDocument.setContent(byteArrayOutputStream.toByteArray());
            } else {
                fileDocument.setContent(new byte[0]);
                filePersistenceService.persistContent(fileDocument.getId(), byteArrayOutputStream);
            }
        } catch (IOException e) {
            log.error("生成缩略图失败: {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * 将输入流转换为 WebP 格式并保存到指定文件
     * @param originImageStream 原始图片的输入流
     * @param destFile 目标文件，必须是 WebP 格式
     */
    public static void convertToWebpFile(InputStream originImageStream, File destFile) {
        // 命令: magick - webp:output.webp
        CommandLine commandLine = new CommandLine("magick");
        commandLine.addArgument("-");
        commandLine.addArgument("-quality");
        commandLine.addArgument("80");
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
     * 使用单一的 ImageMagick 命令，从 InputStream 对图片进行条件性缩放和质量调整。
     * 只有当图片尺寸大于目标尺寸时，才会进行缩放。
     *
     * @param inputStream 源图片输入流
     * @param q           质量 (字符串 "0.0" 到 "1.0", 默认 0.8)
     * @param w           目标最大宽度 (字符串)
     * @param h           目标最大高度 (字符串, 可选)
     * @param outputStream 输出流，用于接收处理后的 png 图片数据
     */
    public static void cropImage(InputStream inputStream, String q, String w, String h, OutputStream outputStream) {
        cropImage(inputStream, q, w, h, outputStream, null);
    }

    /**
     * 使用单一的 ImageMagick 命令，从 InputStream 对图片进行条件性缩放和质量调整。
     * 只有当图片尺寸大于目标尺寸时，才会进行缩放。
     *
     * @param inputStream 源图片输入流
     * @param q           质量 (字符串 "0.0" 到 "1.0", 默认 0.8)
     * @param w           目标最大宽度 (字符串)
     * @param h           目标最大高度 (字符串, 可选)
     * @param outputStream 输出流，用于接收处理后的 png 图片数据
     * @param outputFormat 输出格式 (png 和 gif)
     */
    public static void cropImage(InputStream inputStream, String q, String w, String h, OutputStream outputStream, String outputFormat) {
        // 1. 解析输入参数
        double quality = parseQuality(q);
        int targetWidth = parseDimension(w);
        int targetHeight = parseDimension(h);

        // 如果没有提供有效的目标宽度，则不进行任何处理 (或根据业务抛出异常)
        if (targetWidth <= 0) {
            log.warn("Invalid target width provided. Skipping image processing.");
            // 可以选择直接将输入流复制到输出流，或者什么都不做
            // IoUtil.copy(inputStream, outputStream);
            return;
        }
        if (outputFormat == null) {
            outputFormat = "png";
        }

        // 2. 构建单一的、条件性的 ImageMagick 命令
        CommandLine cmdLine;
        if ("gif".equals(outputFormat)) {
            cmdLine = buildConditionalResizeGIFCommand(targetWidth, targetHeight);
        } else {
            cmdLine = buildConditionalResizeCommand(targetWidth, targetHeight, quality);
        }

        // 3. 执行命令，将输入流管道连接到命令的标准输入
        try {
            CommandUtil.execCommand(cmdLine, inputStream, outputStream);
        } catch (Exception e) {
            log.error("Failed to execute ImageMagick command.", e);
            throw new RuntimeException("Image processing failed", e);
        }
    }

    /**
     * 构建一个使用条件缩放的 ImageMagick 命令行。
     * @param targetWidth  目标宽度
     * @param targetHeight 目标高度 (如果<0则忽略)
     * @param quality      图片质量 (0.0-1.0)
     * @return 构建好的命令行对象
     */
    private static CommandLine buildConditionalResizeCommand(int targetWidth, int targetHeight, double quality) {
        CommandLine cmdLine = new CommandLine("magick");

        // 输入源是标准输入
        cmdLine.addArgument("-", false);

        // 使用 -thumbnail 命令，它比 -resize 更快，并且会移除图片的多余元数据
        cmdLine.addArgument("-thumbnail", false);

        // 构建几何尺寸参数，并添加 ">" 后缀
        StringBuilder geometry = new StringBuilder();
        geometry.append(targetWidth);
        if (targetHeight > 0) {
            geometry.append("x").append(targetHeight);
        }
        // 关键：添加 ">" 实现条件缩放
        geometry.append(">");

        cmdLine.addArgument(geometry.toString(), false);

        // 添加质量和输出格式参数
        cmdLine.addArgument("-quality", false);
        cmdLine.addArgument(String.valueOf((int) (quality * 100)), false);

        // 输出目标是标准输出，格式为 png
        cmdLine.addArgument("png:-", false);

        return cmdLine;
    }

    /**
     * 构建一个使用条件缩放的 ImageMagick 命令行，专门用于处理 GIF 图片以保持动画效果。
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度 (如果<0则忽略)
     * @return 构建好的命令行对象
     */
    private static CommandLine buildConditionalResizeGIFCommand(int targetWidth, int targetHeight) {
        CommandLine cmdLine = new CommandLine("magick");

        cmdLine.addArgument("-", false);

        cmdLine.addArgument("-coalesce", false);
        cmdLine.addArgument("-resize", false);

        StringBuilder geometry = new StringBuilder();
        geometry.append(targetWidth);
        if (targetHeight > 0) {
            geometry.append("x").append(targetHeight);
        }
        geometry.append(">");

        cmdLine.addArgument(geometry.toString(), false);

        cmdLine.addArgument("-layers", false);
        cmdLine.addArgument("optimize", false);

        cmdLine.addArgument("gif:-", false);

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
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            CommandLine cmdLine = new CommandLine("magick");
            cmdLine.addArgument("identify", false);
            cmdLine.addArgument("-format", false);
            cmdLine.addArgument(ImageFormat.DEFAULT_FORMAT_PARAM, false);
            cmdLine.addArgument(imageFile.getAbsolutePath(), false);
            CommandUtil.execCommand(cmdLine, null, outputStream);

            String output = IoUtil.toStr(outputStream, StandardCharsets.UTF_8);
            if (CharSequenceUtil.isBlank(output)) {
                log.error("ImageMagick identify command returned empty output for file: {}", imageFile.getAbsolutePath());
                throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "获取图片信息失败: identify 命令返回为空");
            }
            return ImageFormat.getImageFormat(output);
        } catch (IOException e) {
            log.error("Failed to identify image format: {}", imageFile.getAbsolutePath(), e);
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "获取图片信息失败");
        }

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

    // parseQuality 和 parseDimension 辅助方法保持不变
    private static double parseQuality(String q) {
        if (CharSequenceUtil.isBlank(q)) return 0.8;
        try {
            double quality = Double.parseDouble(q);
            return (quality >= 0 && quality <= 1) ? quality : 0.8;
        } catch (NumberFormatException e) {
            return 0.8;
        }
    }

    private static int parseDimension(String dim) {
        if (CharSequenceUtil.isBlank(dim)) return -1;
        try {
            return Integer.parseInt(dim);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static void checkFile(File srcFile) {
        if (srcFile == null || !srcFile.exists() || !srcFile.isFile()) {
            log.error("Invalid source file: {}", srcFile);
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
    }

}

package com.jmal.clouddisk.media;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.lang.ObjectId;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
     * @param update org.springframework.data.mongodb.core.query.UpdateDefinition
     */
    public void generateThumbnail(File file, Update update) {
        try (InputStream stream = cropImage(file, "1", "256", "256")) {
            update.set("content", IoUtil.readBytes(stream));
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
        Process process = RuntimeUtil.exec("magick", "-", "webp:" + destFile.getAbsolutePath());
        // 输入原图数据到stdin
        try (OutputStream procIn = process.getOutputStream()) {
            originImageStream.transferTo(procIn);
        }
    }

    /**
     * 将输入流转换为 WebP 格式的 InputStream
     * @param originImageStream 原始图片的输入流
     * @return 转换后的 WebP 格式的 InputStream
     * @throws IOException 如果转换过程中发生 I/O 错误
     */
    public static InputStream convertToWebp(InputStream originImageStream) throws IOException {
        Process process = RuntimeUtil.exec("magick", "-", "webp:-");

        // 输入原图数据到stdin
        try (OutputStream procIn = process.getOutputStream()) {
            originImageStream.transferTo(procIn);
        }

        // 调用方负责关闭
        return process.getInputStream();
    }

    /**
     * 将图片转换为 WebP 格式并替换原文件
     * @param srcFile 源文件，必须是图片格式
     * @param destFile 目标文件，必须是 WebP 格式
     * @param deleteSrc 是否删除源文件
     */
    public static void replaceWebp(File srcFile, File destFile, boolean deleteSrc) {
        log.debug("Converting image to WebP format: {} -> {}", srcFile.getAbsolutePath(), destFile.getAbsolutePath());
        // 命令: convert input.jpg -quality 80 output.webp
        try {
            RuntimeUtil.exec("magick",
                    srcFile.getAbsolutePath(),
                    destFile.getAbsolutePath()
            );
        } finally {
            if (deleteSrc) {
                FileUtil.del(srcFile);
            }
        }
    }

    /**
     * 生成带水印的缩略图 (替代 Thumbnails.of(...).watermark(...))
     *
     * @param originalImage  源文件
     * @param watermarkImage 水印图片文件
     * @param outputImage    目标文件
     * @param width          目标宽度
     * @param height         目标高度
     * @param gravity        水印位置 (e.g., "SouthEast" for bottom-right)
     * @param quality        输出图片质量 (1-100)
     */
    public void createWatermarkedThumbnail(File originalImage, File watermarkImage, File outputImage, int width, int height, String gravity, int quality) {
        log.info("Creating watermarked thumbnail for '{}' -> '{}'", originalImage, outputImage);

        // 命令: convert original.jpg -resize 1280x1024 \
        //       watermark.png -gravity SouthEast -compose over -composite \
        //       -quality 80 watermarked_thumbnail.jpg
        executeCommand("convert",
                originalImage.getAbsolutePath(),
                "-resize", width + "x" + height,
                watermarkImage.getAbsolutePath(),
                "-gravity", gravity,
                "-compose", "over",
                "-composite",
                "-quality", String.valueOf(quality),
                outputImage.getAbsolutePath()
        );
    }

    /**
     * 使用 ImageMagick 替换原有的 imageCrop 方法。
     * 根据给定参数对图片进行缩放和质量调整，并返回 JPG 格式的字节数组。
     *
     * @param srcFile 源文件
     * @param q       剪裁后的质量 (字符串 "0.0" 到 "1.0")
     * @param w       剪裁后的宽度 (字符串)
     * @param h       剪裁后的高度 (字符串)
     * @return 剪裁和转换后的 InputStream, 由调用方负责关闭。
     */
    public static InputStream cropImage(File srcFile, String q, String w, String h) throws IOException, InterruptedException {
        if (srcFile == null || !srcFile.exists() || !srcFile.isFile()) {
            log.error("Invalid source file: {}", srcFile);
            throw new CommonException(ExceptionType.FILE_NOT_FIND);
        }
        // 1. 获取源图片尺寸
        int[] dimensions = getImageDimensions(srcFile);
        if (dimensions == null) {
            log.error("Could not get dimensions for file, aborting crop: {}", srcFile.getAbsolutePath());
            return new FileInputStream(srcFile);
        }
        int srcWidth = dimensions[0];
        int srcHeight = dimensions[1]; // 现在需要获取高度

        // 2. 解析输入参数
        double quality = parseQuality(q);
        int targetWidth = parseDimension(w);
        int targetHeight = parseDimension(h); // 解析目标高度

        // 3. 构建 ImageMagick 命令
        List<String> command = new ArrayList<>();
        command.add("magick");
        command.add(srcFile.getAbsolutePath());

        // 4. 根据完整的原始逻辑添加 -resize 参数
        // 只有当目标宽度有效且小于源宽度时，才进行缩放
        if (targetWidth > 0 && srcWidth > targetWidth) {
            command.add("-thumbnail");
            // 如果目标高度也有效且小于源高度，则使用 WxH 边界框
            if (targetHeight > 0 && srcHeight > targetHeight) {
                command.add(targetWidth + "x" + targetHeight);
            } else {
                // 否则 (高度未指定或不造成缩小)，仅根据宽度缩放
                command.add(targetWidth + "x");
            }
        }

        // 5. 添加质量和输出格式参数
        command.add("-quality");
        command.add(String.valueOf((int) (quality * 100))); // ImageMagick 质量是 0-100
        String suffix = FileUtil.extName(srcFile);
        command.add(suffix + ":-"); // 指定输出格式为 JPG, 并输出到 stdout

        // 6. 执行命令并获取 InputStream
        return executeCommandAndGetInputStream(command.toArray(new String[0]));
    }

    /**
     * 获取图片尺寸
     *
     * @param imageFile 图片文件
     * @return 一个包含 [width, height] 的数组，如果失败则返回 null
     */
    public static int[] getImageDimensions(File imageFile) {
        // 命令: identify -format "%w %h" image.jpg
        String output = executeCommand("identify", "-format", "%w %h", imageFile.getAbsolutePath());
        if (CharSequenceUtil.isBlank(output)) {
            return null;
        }
        String[] dimensions = output.trim().split(" ");
        if (dimensions.length == 2) {
            try {
                return new int[]{Integer.parseInt(dimensions[0]), Integer.parseInt(dimensions[1])};
            } catch (NumberFormatException e) {
                log.error("Failed to parse dimensions from identify output: {}", output);
                return null;
            }
        }
        return null;
    }

    /**
     * 执行命令并返回 InputStream.
     */
    private static InputStream executeCommandAndGetInputStream(String... command) throws IOException, InterruptedException {
        log.info("Executing command for inputStream: {}", String.join(" ", command));
        Process process = RuntimeUtil.exec(command);
        outputError(command, process);
        return process.getInputStream();
    }

    private static void outputError(String[] command, Process process) throws IOException, InterruptedException {
        // 先等进程执行结束
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Command timed out: " + String.join(" ", command));
        }

        // 读取错误输出
        String errorOutput = IoUtil.read(process.getErrorStream(), StandardCharsets.UTF_8);

        if (process.exitValue() != 0) {
            printError(command, process, errorOutput);
        }
    }

    private static void printError(String[] command, Process process, String errorOutput) {
        log.error("Command failed with exit code {}: {}", process.exitValue(), java.lang.String.join(" ", command));
        log.error("Error output:\n{}", errorOutput);
    }

    /**
     * 解析质量参数 (0.0 - 1.0), 默认 0.8.
     */
    private static double parseQuality(String q) {
        if (q == null || q.isBlank()) {
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
        if (dim == null || dim.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(dim);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String executeCommand(String... command) {
        try {
            log.debug("Executing command: {}", String.join(" ", command));
            return RuntimeUtil.execForStr(StandardCharsets.UTF_8, command);
        } catch (IORuntimeException e) {
            log.error("Error executing command: {}", String.join(" ", command), e);
        }
        return null;
    }
}

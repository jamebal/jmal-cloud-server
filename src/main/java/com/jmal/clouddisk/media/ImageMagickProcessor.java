package com.jmal.clouddisk.media;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.ObjectId;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
        byte[] imageBytes = cropImage(file, "1", "256", "256");
        update.set("content", imageBytes);
    }

    /**
     * 生成带水印的缩略图 (替代 Thumbnails.of(...).watermark(...))
     * @param originalImage 源文件
     * @param watermarkImage 水印图片文件
     * @param outputImage 目标文件
     * @param width 目标宽度
     * @param height 目标高度
     * @param gravity 水印位置 (e.g., "SouthEast" for bottom-right)
     * @param quality 输出图片质量 (1-100)
     */
    public void createWatermarkedThumbnail(File originalImage, File watermarkImage, File outputImage, int width, int height, String gravity, int quality) throws IOException, InterruptedException {
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
     * @return 剪裁和转换后的 JPG 图片字节数组
     */
    public static byte[] cropImage(File srcFile, String q, String w, String h) {
        try {
            // 1. 获取源图片尺寸
            int[] dimensions = getImageDimensions(srcFile);
            if (dimensions == null) {
                log.error("Could not get dimensions for file, aborting crop: {}", srcFile.getAbsolutePath());
                return new byte[0];
            }
            int srcWidth = dimensions[0];
            int srcHeight = dimensions[1]; // 现在需要获取高度

            // 2. 解析输入参数
            double quality = parseQuality(q);
            int targetWidth = parseDimension(w);
            int targetHeight = parseDimension(h); // 解析目标高度

            // 3. 构建 ImageMagick 命令
            List<String> command = new ArrayList<>();
            command.add("convert");
            command.add(srcFile.getAbsolutePath());

            // 4. 【核心修正】根据完整的原始逻辑添加 -resize 参数
            // 只有当目标宽度有效且小于源宽度时，才进行缩放
            if (targetWidth > 0 && srcWidth > targetWidth) {
                command.add("-resize");
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
            command.add("png:-"); // 指定输出格式为 JPG, 并输出到 stdout

            // 6. 执行命令并获取字节数组输出
            return executeCommandAndGetOutputBytes(command.toArray(new String[0]));

        } catch (IOException | InterruptedException e) {
            log.error("Failed to crop image '{}': {}", srcFile.getName(), e.getMessage(), e);
            Thread.currentThread().interrupt(); // 恢复中断状态
            return new byte[0];
        }
    }

    /**
     * 获取图片尺寸
     * @param imageFile 图片文件
     * @return 一个包含 [width, height] 的数组，如果失败则返回 null
     */
    public static int[] getImageDimensions(File imageFile) throws IOException, InterruptedException {
        // 命令: identify -format "%w %h" image.jpg
        String output = executeCommand("identify", "-format", "%w %h", imageFile.getAbsolutePath());
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

    // --- 辅助方法 ---

    /**
     * 执行命令并返回标准输出的字节数组。
     */
    private static byte[] executeCommandAndGetOutputBytes(String... command) throws IOException, InterruptedException {
        log.debug("Executing command for byte output: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();

        byte[] outputBytes;
        try (InputStream inputStream = process.getInputStream()) {
            outputBytes = inputStream.readAllBytes();
        }

        String errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("Command timed out: " + String.join(" ", command));
        }

        if (process.exitValue() != 0) {
            printError(command, process, errorOutput);
            throw new IOException("ImageMagick command execution failed. See logs for details.");
        }

        return outputBytes;
    }

    public static void main(String[] args) {
        // 测试生成缩略图
        try {
            File srcFile = new File("/Users/jmal/Pictures/截图/截屏2024-11-23 13.02.03.png");
            byte[] thumbnailBytes = cropImage(srcFile, "1", "256", "256");
            if (thumbnailBytes.length > 0) {
                log.info("Thumbnail created successfully with size: {} bytes", thumbnailBytes.length);
            } else {
                log.error("Failed to create thumbnail.");
            }

            // 将生成的缩略图保存到文件以验证
            File outputFile = new File("/Users/jmal/Downloads/thumbnail_output.png");
            FileUtil.writeBytes(thumbnailBytes, outputFile);

        } catch (Exception e) {
            log.error("Error during thumbnail creation: {}", e.getMessage(), e);
        }
    }

    private static void printError(String[] command, Process process, String errorOutput) {
        log.error("Command failed with exit code {}: {}", process.exitValue(), String.join(" ", command));
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

    private static String executeCommand(String... command) throws IOException, InterruptedException {
        log.debug("Executing command: {}", String.join(" ", command));
        ProcessBuilder pb = new ProcessBuilder(command);
        Process process = pb.start();

        // 读取标准输出和标准错误，以防子进程缓冲区被填满
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String error = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

        if (!process.waitFor(60, TimeUnit.SECONDS)) { // 图片处理可能耗时较长
            process.destroyForcibly();
            throw new IOException("Command timed out: " + String.join(" ", command));
        }

        if (process.exitValue() != 0) {
            printError(command, process, error);
            throw new IOException("Command execution failed. See logs for details.");
        }
        return output;
    }
}

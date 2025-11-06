package com.jmal.clouddisk;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * 文件传输模拟器
 * 该程序用于测试文件监控系统。它会：
 * 1. 在源目录 (SOURCE_DIR) 中创建一些模拟文件。
 * 2. 将这些文件逐个、分片地复制到目标目录 (TARGET_DIR) 下的一个子目录中。
 * 3. 复制过程中使用 ".downloading" 作为临时文件名，完成后再重命名。
 */
public class FileTransferSimulator {

    // --- 1. 可配置的静态变量 ---
    /** 源目录：模拟文件来源的地方 */
    private static final String SOURCE_DIR_NAME = "/Users/jmal/Downloads/未命名文件夹";
    /** 目标目录：文件将被复制到的地方 */
    private static final String TARGET_DIR_NAME = "/Users/jmal/temp/filetest/rootpath-dev/test/新建文件夹";

    /** 文件复制时的分片大小 (1MB) */
    private static final int CHUNK_SIZE = 1024 * 1024;
    /** 每个分片写入后的暂停时间（毫秒），用于模拟网络或磁盘延迟 */
    private static final int PAUSE_BETWEEN_CHUNKS_MS = 500;

    /**
     * 程序主入口
     */
    public static void main(String[] args) {
        Path sourcePath = Paths.get(SOURCE_DIR_NAME);
        Path targetPath = Paths.get(TARGET_DIR_NAME);

        System.out.println("===== 文件传输模拟开始 =====");
        System.out.println("源目录: " + sourcePath.toAbsolutePath());
        System.out.println("目标目录: " + targetPath.toAbsolutePath());

        try {
            // --- 2. 准备模拟环境 ---
            setupEnvironment(sourcePath);

            // --- 3. 执行核心传输逻辑 ---
            transferAllFiles(sourcePath, targetPath);

        } catch (IOException e) {
            System.err.println("\n模拟过程中发生严重错误: " + e.getMessage());
        }
    }

    /**
     * 遍历源目录并处理其中所有文件
     * @param sourceDir 源目录
     * @param targetDir 目标根目录
     */
    public static void transferAllFiles(Path sourceDir, Path targetDir) throws IOException {
        // 在目标目录中创建与源目录同名的子目录
        Path targetSubDir = targetDir.resolve(sourceDir.getFileName());
        Files.createDirectories(targetSubDir);
        System.out.println("\n已在目标位置创建子目录: " + targetSubDir);

        // 使用 try-with-resources 确保目录流被正确关闭
        try (Stream<Path> fileStream = Files.list(sourceDir).parallel()) {
            fileStream
                    .filter(Files::isRegularFile) // 只处理文件，忽略子目录
                    .forEach(sourceFile -> {
                        System.out.println("\n--------------------------------------------------");
                        System.out.println(">>> 开始处理文件: " + sourceFile.getFileName());
                        try {
                            processSingleFileWithIntermediateClose(sourceFile, targetSubDir);
                        } catch (IOException e) {
                            System.err.println("处理文件 " + sourceFile.getFileName() + " 时失败: " + e.getMessage());
                            // 即使一个文件失败，也继续处理下一个
                        }
                    });
        }
        System.out.println("\n--------------------------------------------------");
        System.out.println("所有文件处理完毕。");
    }

    /**
     * 处理单个文件的分片复制（模拟中断和续传）
     * 每次写入一个分片后都关闭文件流，以强制触发 CLOSE_WRITE 事件。
     * @param sourceFile 源文件
     * @param targetSubDir 目标子目录
     */
    private static void processSingleFileWithIntermediateClose(Path sourceFile, Path targetSubDir) throws IOException {
        String fileName = sourceFile.getFileName().toString();
        Path tempFilePath = targetSubDir.resolve(fileName + ".downloading");
        Path finalFilePath = targetSubDir.resolve(fileName);

        Files.deleteIfExists(tempFilePath);
        Files.deleteIfExists(finalFilePath);

        System.out.println("创建临时文件: " + tempFilePath.getFileName());

        try (InputStream in = Files.newInputStream(sourceFile)) {
            byte[] buffer = new byte[CHUNK_SIZE];
            int bytesRead;
            long totalBytesWritten = 0;
            long fileSize = Files.size(sourceFile);

            while ((bytesRead = in.read(buffer)) != -1) {
                // **关键改动**: 使用一个独立的 try-with-resources 块来写入每个分片
                // 这将导致每次循环都打开、写入、然后关闭文件
                try (FileOutputStream fos = new FileOutputStream(tempFilePath.toFile(), true); // true 表示 append 模式
                     FileChannel channel = fos.getChannel()) {

                    fos.write(buffer, 0, bytesRead);
                    channel.force(true); // 确保写入磁盘
                } // <--- 在这里，fos 和 channel 会被自动 close()，触发 IN_CLOSE_WRITE

                totalBytesWritten += bytesRead;
                System.out.println("  - 写入、同步并关闭了 " + formatSize(bytesRead) + "，总进度: " + totalBytesWritten + " / " + fileSize + "字节");

                // 暂停，给监控系统反应的时间
                try {
                    TimeUnit.MILLISECONDS.sleep(PAUSE_BETWEEN_CHUNKS_MS * 2);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("文件写入过程被中断", e);
                }
            }
        }

        System.out.println("所有分片写入完成。");
        System.out.println("重命名 '" + tempFilePath.getFileName() + "' -> '" + finalFilePath.getFileName() + "'");
        Files.move(tempFilePath, finalFilePath, StandardCopyOption.ATOMIC_MOVE);
        System.out.println("<<< 文件 '" + fileName + "' 处理成功！");
    }

    /**
     * 准备模拟环境：创建源目录和几个不同大小的虚拟文件
     * @param sourceDir 要创建的源目录路径
     */
    private static void setupEnvironment(Path sourceDir) throws IOException {
        System.out.println("\n正在设置模拟环境...");
        Files.createDirectories(sourceDir);

        // 创建几个模拟文件
        createDummyFile(sourceDir.resolve("文件1.abc"), 3 * 1024 * 1024 + 512 * 1024); // 3.5 MB
        createDummyFile(sourceDir.resolve("文件2.abc"), 1024 * 1024); // 1 MB
        createDummyFile(sourceDir.resolve("small_archive.abc"), 200 * 1024); // 200 KB (小于一个分片)
        for (int i = 1; i < 100; i++) {
            createDummyFile(sourceDir.resolve("big" + i +"_archive.abc"), 100 * 1024 * 1024);
        }

        System.out.println("环境设置完毕，源目录中已创建了3个模拟文件。");
    }

    /**
     * 创建指定大小的虚拟文件
     */
    private static void createDummyFile(Path path, long size) throws IOException {
        if (Files.exists(path)) {
            System.out.println("  - 虚拟文件已存在，跳过创建: " + path.getFileName());
            return;
        }
        System.out.println("  - 创建虚拟文件: " + path.getFileName() + " (" + formatSize(size) + ")");
        try (OutputStream out = Files.newOutputStream(path)) {
            byte[] buffer = new byte[8192];
            Random random = new Random();
            long remaining = size;
            while (remaining > 0) {
                random.nextBytes(buffer);
                int toWrite = (int) Math.min(buffer.length, remaining);
                out.write(buffer, 0, toWrite);
                remaining -= toWrite;
            }
        }
    }

    /**
     * 格式化文件大小为可读字符串
     */
    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}

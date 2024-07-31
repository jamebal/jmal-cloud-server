package com.jmal.clouddisk.media;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import com.jmal.clouddisk.service.Constants;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jmal.clouddisk.util.FFMPEGUtils.getWaitingForResults;

@Slf4j
public class HeifUtils {

    /**
     * 将heic转换为jpg
     */
    public static String heifConvert(String inputPath, Path outputParentPath) {
        if (!checkHeif()) {
            return null;
        }
        Path filepath = Paths.get(inputPath + ".jpg");
        String outputPath = Paths.get(outputParentPath.toString(), filepath.getFileName().toString()).toString();
        if (FileUtil.exist(outputPath)) {
            return outputPath;
        }
        try {
            ProcessBuilder processBuilder = heifConvert(inputPath, outputPath);
            Process process = processBuilder.start();
            StringBuilder output = printProcessInfo(process);
            outputPath = getWaitingForResults(outputPath, processBuilder, process);

            delAuxiliaryImagePath(output);

            // 打印命令 用空格连接
            String command = String.join(" ", processBuilder.command());
            log.info("heif-convert 执行成功, command: \r\n{}", command);

            if (outputPath != null) {
                PathUtil.move(Path.of(outputPath), filepath, true);
                return filepath.toString();
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            return null;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 删除辅助图像文件
     * @param output process输出
     */
    private static void delAuxiliaryImagePath(StringBuilder output) {
        // 解析输出，查找辅助图像文件名, 并删除
        String auxiliaryImagePath = null;
        Pattern pattern = Pattern.compile("Auxiliary image written to (.+)");
        Matcher matcher = pattern.matcher(output.toString());
        if (matcher.find()) {
            auxiliaryImagePath = matcher.group(1);
        }
        if (auxiliaryImagePath != null) {
            FileUtil.del(auxiliaryImagePath);
        }
    }

    /**
     * 将heic转换为jpg
     * @param filepath heic文件绝对路径
     * @return ProcessBuilder
     */
    private static ProcessBuilder heifConvert(String filepath, String outputPath) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                Constants.HEIF_CONVERT,
                filepath,
                outputPath
        );
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }

    private static StringBuilder printProcessInfo(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (InputStream inputStream = process.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        return output;
    }

    /**
     * 检查是否安装了heif-convert
     */
    private static boolean checkHeif() {
        try {
            Process process = Runtime.getRuntime().exec(Constants.HEIF_CONVERT);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("output")) {
                    return true;
                }
            }
            return true;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return false;
    }
}

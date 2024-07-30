package com.jmal.clouddisk.media;

import cn.hutool.core.io.FileUtil;
import com.jmal.clouddisk.service.Constants;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import static com.jmal.clouddisk.util.FFMPEGUtils.getWaitingForResults;

@Slf4j
public class HeifUtils {

    /**
     * 将heic转换为jpg
     */
    public static String heifConvert(String inputPath) {
        if (!checkHeif()) {
            return null;
        }
        String outputPath = inputPath + ".jpg";
        if (FileUtil.exist(outputPath)) {
            return outputPath;
        }
        try {
            ProcessBuilder processBuilder = heifConvert(inputPath, outputPath);
            Process process = processBuilder.start();
            return getWaitingForResults(outputPath, processBuilder, process);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            return null;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
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


    /**
     * 检查是否安装了heif-convert
     */
    public static boolean checkHeif() {
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

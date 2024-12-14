package com.jmal.clouddisk.util;

import cn.hutool.core.io.FileUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

@Slf4j
public class FFMPEGUtils {
    public static void printErrorInfo(ProcessBuilder processBuilder, Process process) throws IOException {
        printErrorInfo(processBuilder);
        // 打印process的错误输出
        try (InputStream inputStream = process.getErrorStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            while ((line = reader.readLine()) != null) {
                log.error(line);
            }
        }
    }

    public static void printErrorInfo(ProcessBuilder processBuilder) {
        // 打印命令 用空格连接
        String command = String.join(" ", processBuilder.command());
        log.error("命令 执行失败, command: \r\n{}", command);
    }

    public static void printSuccessInfo(ProcessBuilder processBuilder) {
        // 打印命令 用空格连接
        String command = String.join(" ", processBuilder.command());
        log.info("命令 执行成功, command: \r\n{}", command);
    }

    /**
     * 等待处理结果
     * @param outputPath 输出文件路径
     * @param processBuilder 处理器
     * @return outputPath
     */
    public static String getWaitingForResults(String outputPath, ProcessBuilder processBuilder, Process process) throws IOException, InterruptedException {
        return getWaitingForResults(outputPath, processBuilder, process, 12);
    }

    public static String getWaitingForResults(String outputPath, ProcessBuilder processBuilder, Process process, int waitSeconds) throws InterruptedException, IOException {
        boolean finished = process.waitFor(waitSeconds, TimeUnit.SECONDS);
        try {
            log.debug("finished: {}", finished);
            log.debug("exitValue: {}", process.exitValue());
            if (finished && process.exitValue() == 0) {
                if (FileUtil.exist(outputPath)) {
                    return outputPath;
                } else {
                    log.error("处理完成后输出文件不存在。");
                }
            }
        } catch (IllegalThreadStateException e) {
            log.error(e.getMessage());
        }
        if (!finished) {
            // 超时后处理
            process.destroy(); // 尝试正常终止
            process.destroyForcibly(); // 强制终止
            log.error("进程超时并被终止。");
            TimeUnit.SECONDS.sleep(2);
            printErrorInfo(processBuilder);
        } else {
            // 进程结束但退出码非0
            printErrorInfo(processBuilder, process);
        }
        if (FileUtil.exist(outputPath)) {
            return outputPath;
        }
        return null;
    }

}

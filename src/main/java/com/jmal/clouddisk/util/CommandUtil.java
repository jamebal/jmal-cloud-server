package com.jmal.clouddisk.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.PumpStreamHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Slf4j
public class CommandUtil {

    /**
     * 同步执行外部命令(30秒超时)
     * @param cmdLine 命令行参数
     * @param inputStream 输入流，用于提供命令执行所需的输入数据
     * @param outputStream 输出流，用于接收命令执行结果
     */
    public static void execCommand(CommandLine cmdLine, InputStream inputStream, OutputStream outputStream) {
        execCommand(cmdLine, inputStream, outputStream, Duration.ofSeconds(30));
    }

    /**
     * 同步执行外部命令
     * @param cmdLine 命令行参数
     * @param inputStream 输入流，用于提供命令执行所需的输入数据
     * @param outputStream 输出流，用于接收命令执行结果
     * @param timeout 超时时间，默认 30 秒
     */
    public static void execCommand(CommandLine cmdLine, InputStream inputStream, OutputStream outputStream, Duration timeout) {
        DefaultExecutor executor = DefaultExecutor.builder().get();
        try(ByteArrayOutputStream stdErr = new ByteArrayOutputStream()) {
            // 参数：标准输出、标准错误、标准输入
            PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream, stdErr, inputStream);
            executor.setStreamHandler(streamHandler);

            ExecuteWatchdog watchdog = ExecuteWatchdog.builder().setTimeout(timeout).get();
            executor.setWatchdog(watchdog);

            try {
                int exitValue = executor.execute(cmdLine);
                log.debug("command exec success, command: {}", String.join(" ", cmdLine.toStrings()));
                if (exitValue != 0) {
                    String errMsg = stdErr.toString(StandardCharsets.UTF_8);
                    log.error("command exec failed, command: {}, exit code: {}, stderr: {}", String.join(" ", cmdLine.toStrings()), exitValue, errMsg);
                }
            } catch (IOException e) {
                String errMsg = stdErr.toString(StandardCharsets.UTF_8);
                if (watchdog.killedProcess()) {
                    log.error("command exec timeout, command: {}, stderr: {}", String.join(" ", cmdLine.toStrings()), errMsg);
                } else {
                    log.error("command exec error, command: {}, stderr: {}, {}", String.join(" ", cmdLine.toStrings()), errMsg, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("command exec error, command: {}, {}", String.join(" ", cmdLine.toStrings()), e.getMessage());
        }
    }

}

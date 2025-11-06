package com.jmal.clouddisk.util;

import lombok.extern.slf4j.Slf4j;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;

@Slf4j
public class CharsetDetector {

    private static final int SAMPLE_SIZE = 8 * 1024; // 8KB
    private static final int BINARY_CHECK_LENGTH = 1024;
    private static final double MAX_NULL_BYTE_RATIO = 0.05;
    private static final double MAX_CONTROL_CHAR_RATIO = 0.1;

    /**
     * 从字节数组检测字符集（多重策略）
     */
    public static Charset detect(byte[] bytes, int length) throws Exception {
        if (bytes == null || length <= 0) {
            return null;
        }

        // 策略1: 检查是否为二进制文件
        if (isBinaryContent(bytes, length)) {
            return null;
        }

        // 策略2: 检查 BOM
        Charset bomCharset = detectBOM(bytes, length);
        if (bomCharset != null) {
            return bomCharset;
        }

        // 策略3: 使用 UniversalDetector
        Charset detectedCharset = detectByUniversalDetector(bytes, length);
        if (detectedCharset != null && identify(bytes, detectedCharset.newDecoder())) {
            return detectedCharset;
        }

        return null;
    }

    /**
     * 检测文件（只读取头部）
     */
    public static Charset detect(File file) {
        try (InputStream is = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[SAMPLE_SIZE];
            int bytesRead = is.read(buffer);

            if (bytesRead == -1) {
                return null;
            }
            return detect(buffer, bytesRead);
        } catch (Exception e) {
            log.warn("检查文件字符集失败: {}", file.getAbsolutePath(), e);
        }
        return null;
    }

    /**
     * 判断是否为二进制内容
     */
    private static boolean isBinaryContent(byte[] bytes, int length) {
        int nullCount = 0;
        int controlCharCount = 0;
        int checkLength = Math.min(length, BINARY_CHECK_LENGTH); // 只检查前1KB

        for (int i = 0; i < checkLength; i++) {
            byte b = bytes[i];

            // NULL 字节
            if (b == 0) {
                nullCount++;
            }
            // 控制字符（排除常见的换行、制表符等）
            else if (b > 0 && b < 32 && b != '\n' && b != '\r' && b != '\t') {
                controlCharCount++;
            }
        }

        // 如果包含较多null字节或控制字符，认为是二进制
        double nullRatio = (double) nullCount / checkLength;
        double controlRatio = (double) controlCharCount / checkLength;

        return nullRatio > MAX_NULL_BYTE_RATIO || controlRatio > MAX_CONTROL_CHAR_RATIO;
    }

    /**
     * 检测 BOM (Byte Order Mark)
     */
    private static Charset detectBOM(byte[] bytes, int length) {
        if (length >= 3 && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            return StandardCharsets.UTF_8;
        }
        if (length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            // UTF-16LE or UTF-32LE
            if (length >= 4 && bytes[2] == 0 && bytes[3] == 0) {
                return StandardCharsets.UTF_32LE;
            }
            return StandardCharsets.UTF_16LE;
        }
        if (length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            return StandardCharsets.UTF_16BE;
        }
        if (length >= 4 && bytes[0] == 0 && bytes[1] == 0 && bytes[2] == (byte) 0xFE && bytes[3] == (byte) 0xFF) {
            return StandardCharsets.UTF_32BE;
        }
        return null;
    }

    /**
     * 使用 UniversalDetector 检测
     */
    private static Charset detectByUniversalDetector(byte[] bytes, int length) throws Exception {
        try {
            UniversalDetector detector = new UniversalDetector(null);
            detector.handleData(bytes, 0, length);
            detector.dataEnd();
            String encoding = detector.getDetectedCharset();
            detector.reset();
            if (encoding != null) {
                try {
                    return Charset.forName(encoding);
                } catch (UnsupportedCharsetException e) {
                    throw new UnsupportedCharsetException(encoding);
                }
            }
        } catch (Exception e) {
            throw new Exception(e);
        }
        return null;
    }

    private static boolean identify(byte[] bytes, CharsetDecoder decoder) {
        try {
            // 配置解码器为最严格的模式，一出错就报告
            decoder.onMalformedInput(CodingErrorAction.REPORT);
            decoder.onUnmappableCharacter(CodingErrorAction.REPORT);
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

}

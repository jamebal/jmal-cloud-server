package com.jmal.clouddisk.util;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.CharsetUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestAlgorithm;
import com.jmal.clouddisk.service.Constants;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * @author jmal
 * @Description 文件工具类
 * @Date 2020-06-16 16:24
 */
@Slf4j
public class MyFileUtils {

    public static Set<String> hasContentTypes = Set.of("pdf", "drawio", "mind", "doc", "docx", "xls", "xlsx", "xlsm", "ppt", "pptx", "csv", "tsv", "dotm", "xlt", "xltm", "dot", "dotx", "xlam", "xla", "pages", "epub", "dwg");

    private static final Set<String> BINARY_TYPE_KEYWORDS = Set.of(
            Constants.VIDEO,
            Constants.CONTENT_TYPE_IMAGE,
            Constants.AUDIO,
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz"
    );


    private MyFileUtils() {

    }

    public static String extName(File file) {
        return FileUtil.extName(file.getName()).toLowerCase();
    }

    public static String extName(String fileName) {
        return FileUtil.extName(fileName).toLowerCase();
    }

    private static boolean isBinaryOrCompressedType(String contentType) {
        return BINARY_TYPE_KEYWORDS.stream()
                .anyMatch(contentType::contains);
    }

    public static boolean hasCharset(File file) {
        return getCharset(file) != null;
    }

    public static Charset getCharset(File file) {
        try {
            if (file == null || file.isDirectory()) {
                return null;
            }
            String suffix = MyFileUtils.extName(file.getName());
            String contentType = FileContentTypeUtils.getContentType(suffix);
            if (isBinaryOrCompressedType(contentType)) {
                return null;
            }
            return CharsetDetector.detect(file);
        } catch (Exception e) {
            return null;
        }
    }

    /***
     * 获取文件的字符编码
     * @param file 源文件
     * @return 字符编码
     */
    public static Charset getFileCharset(File file) {
        return CharsetDetector.detect(file);
    }

    public static boolean checkNoCacheFile(File file) {
        try {
            if (file == null) {
                return false;
            }
            if (!file.isFile()) {
                return false;
            }
            if (file.length() == 0) {
                return true;
            }
            String type = FileTypeUtil.getType(file).toLowerCase();
            if (hasContentFile(type)) return true;
            return hasCharset(file);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasContentFile(String type) {
        return hasContentTypes.contains(type);
    }

    public static MessageDigest digest;

    static {
        try {
            digest = MessageDigest.getInstance(DigestAlgorithm.MD5.getValue());
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * GZIP压缩，返回压缩流
     *
     * @param rawInput    原始输入流
     * @param compression 压缩方式
     * @return 压缩后的输入流
     * @throws IOException 发生IO异常
     */
    public static InputStream gzipCompress(InputStream rawInput, String compression) throws IOException {
        if (!"gzip".equalsIgnoreCase(compression)) {
            return rawInput;
        }
        final PipedOutputStream pipedOut = new PipedOutputStream();
        final PipedInputStream pipedIn = new PipedInputStream(pipedOut);

        Thread.startVirtualThread(() -> {
            try (GZIPOutputStream gzipOut = new GZIPOutputStream(pipedOut)) {
                rawInput.transferTo(gzipOut);
            } catch (IOException e) {
                log.error("GZIP压缩失败: {}", e.getMessage(), e);
            } finally {
                try {
                    pipedOut.close();
                } catch (Exception ignore) {
                }
                try {
                    rawInput.close();
                } catch (Exception ignore) {
                }
            }
        });

        return pipedIn;
    }

    /**
     * GZIP解压，返回解压流和字符集
     *
     * @param inputStream 压缩后的 inputStream
     * @param  isGzip      是否是gzip压缩
     * @param  charset     字符集
     * @return 解压后的 inputStream 和字符集
     */
    public static Pair<InputStream, String> gzipDecompress(InputStream inputStream, boolean isGzip, String charset) {
        charset = StrUtil.isNotBlank(charset) ? charset : CharsetUtil.UTF_8;
        if (!isGzip) {
            // 不是gzip压缩，直接返回原流和字符集
            return Pair.of(inputStream, charset);
        }
        try {
            return Pair.of(new GZIPInputStream(inputStream), charset);
        } catch (IOException e) {
            log.error("GZIP解压失败: {}", e.getMessage(), e);
            return Pair.of(new ByteArrayInputStream(new byte[0]), charset);
        }
    }
}



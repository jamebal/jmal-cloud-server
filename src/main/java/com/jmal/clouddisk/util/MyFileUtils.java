package com.jmal.clouddisk.util;

import cn.hutool.core.io.CharsetDetector;
import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.service.Constants;
import lombok.extern.slf4j.Slf4j;
import org.mozilla.universalchardet.UniversalDetector;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * @author jmal
 * @Description 文件工具类
 * @Date 2020-06-16 16:24
 */
@Slf4j
public class MyFileUtils {

    public static List<String> hasContentTypes = Arrays.asList("pdf", "drawio", "mind", "doc", "docx", "xls", "xlsx", "xlsm", "ppt", "pptx", "csv", "tsv", "dotm", "xlt", "xltm", "dot", "dotx", "xlam", "xla", "pages", "epub", "dwg");

    private MyFileUtils() {

    }

    public static void main(String[] args) {
        System.out.println(extName("file"));
    }

    public static String extName(File file) {
        return FileUtil.extName(file.getName()).toLowerCase();
    }

    public static String extName(String fileName) {
        return FileUtil.extName(fileName).toLowerCase();
    }

    public static boolean hasCharset(File file) {
        try {
            if (file == null) {
                return false;
            }
            String suffix = MyFileUtils.extName(file.getName());
            String contentType = FileContentTypeUtils.getContentType(suffix);
            if (file.isDirectory()) {
                return false;
            }
            if (contentType.contains(Constants.VIDEO)) {
                return false;
            }
            if (contentType.contains(Constants.CONTENT_TYPE_IMAGE)) {
                return false;
            }
            if (contentType.contains(Constants.AUDIO)) {
                return false;
            }
            if (contentType.contains("zip")) {
                return false;
            }
            if (contentType.contains("rar")) {
                return false;
            }
            if (contentType.contains("7z")) {
                return false;
            }
            if (contentType.contains("tar")) {
                return false;
            }
            if (contentType.contains("gz")) {
                return false;
            }
            if (contentType.contains("bz2")) {
                return false;
            }
            if (contentType.contains("xz")) {
                return false;
            }
            // 大于250M的文件不检查
            if (file.length() > 250 * 1024 * 1024) {
                return false;
            }
            return CharsetDetector.detect(file) != null;
        } catch (Exception e) {
            return false;
        }
    }

    /***
     * 获取文件的字符编码
     * @param file 源文件
     * @return 字符编码
     */
    public static Charset getFileCharset(File file) {
        try {
            String charset = UniversalDetector.detectCharset(file);
            return StrUtil.isBlank(charset) ? StandardCharsets.UTF_8 : Charset.forName(charset);
        } catch (Exception e) {
            return StandardCharsets.UTF_8;
        }
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
}



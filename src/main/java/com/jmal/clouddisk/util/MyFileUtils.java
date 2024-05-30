package com.jmal.clouddisk.util;

import cn.hutool.core.io.CharsetDetector;
import cn.hutool.core.io.FileTypeUtil;

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
public class MyFileUtils {

    public static List<String> hasContentTypes = Arrays.asList("pdf", "ppt", "pptx", "doc", "docx", "drawio", "mind");

    private MyFileUtils(){

    }

    /***
     * 获取文件的字符编码
     * @param file 源文件
     * @return 字符编码
     */
    public static Charset getFileCharset(File file) {
        Charset charset = CharsetDetector.detect(file, StandardCharsets.UTF_8);
        return charset == null ? StandardCharsets.UTF_8 : charset;
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
            String type = FileTypeUtil.getType(file);
            if (hasContentFile(type)) return true;
            Charset charset = CharsetDetector.detect(file);
            if (charset == null) {
                return false;
            }
            if ("UTF-8".equals(charset.toString())) {
                return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public static boolean hasContentFile(String type) {
        return hasContentTypes.contains(type);
    }
}



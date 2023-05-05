package com.jmal.clouddisk.util;

import cn.hutool.core.io.CharsetDetector;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * @author jmal
 * @Description 文件工具类
 * @Date 2020-06-16 16:24
 */
@Slf4j
public class MyFileUtils {

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
}



package com.jmal.clouddisk.util;

import ch.qos.logback.classic.Logger;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import cn.hutool.core.lang.Console;
import info.monitorenter.cpdetector.io.*;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.stream.Collectors;

/**
 * @Description 文件工具类
 * @Author jmal
 * @Date 2020-06-16 16:24
 */
@Slf4j
public class MyFileUtils {

    /***
     * 读取文件的前${lines}行
     * @param file 源文件
     * @param lines 要读取行数
     * @return 前lines行内容
     */
    public static String readLines(File file,int lines){
        FileReader reader = new FileReader(file, getFileEncode(file));
        return reader.read(new ReaderHandlerString(lines)).toString();
    }

    /***
     * 获取文件的字符编码
     * @param file 源文件
     * @return 字符编码
     */
    public static String getFileEncode(File file) {
        /*
         * detector是探测器，它把探测任务交给具体的探测实现类的实例完成。
         * cpDetector内置了一些常用的探测实现类，这些探测实现类的实例可以通过add方法 加进来，如ParsingDetector、
         * JChardetFacade、ASCIIDetector、UnicodeDetector。
         * detector按照“谁最先返回非空的探测结果，就以该结果为准”的原则返回探测到的
         * 字符集编码。使用需要用到三个第三方JAR包：antlr.jar、chardet.jar和cpdetector.jar
         * cpDetector是基于统计学原理的，不保证完全正确。
         */
        CodepageDetectorProxy detector = CodepageDetectorProxy.getInstance();
        /*
         * ParsingDetector可用于检查HTML、XML等文件或字符流的编码,构造方法中的参数用于
         * 指示是否显示探测过程的详细信息，为false不显示。
         */
        detector.add(new ParsingDetector(false));
        /*
         * JChardetFacade封装了由Mozilla组织提供的JChardet，它可以完成大多数文件的编码
         * 测定。所以，一般有了这个探测器就可满足大多数项目的要求，如果你还不放心，可以
         * 再多加几个探测器，比如下面的ASCIIDetector、UnicodeDetector等。
         */
        // 用到antlr.jar、chardet.jar
        detector.add(JChardetFacade.getInstance());
        // ASCIIDetector用于ASCII编码测定
        detector.add(ASCIIDetector.getInstance());
        // UnicodeDetector用于Unicode家族编码的测定
        detector.add(UnicodeDetector.getInstance());
        java.nio.charset.Charset charset = null;
        try {
            charset = detector.detectCodepage(file.toURI().toURL());
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error(ex.getMessage(), ex);
        }
        if (charset != null){
            return charset.name();
        }
        return "UTF-8";
    }

}

class ReaderHandlerString implements FileReader.ReaderHandler {
    private int lines;
    public ReaderHandlerString(int lines){
        this.lines = lines;
    }
    @Override
    public String handle(BufferedReader reader) throws IOException {
        return reader.lines().limit(this.lines).collect(Collectors.joining("\r\n"));
    }
}



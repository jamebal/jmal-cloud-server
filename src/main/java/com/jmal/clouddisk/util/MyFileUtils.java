package com.jmal.clouddisk.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.FileReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @Description 文件工具类
 * @Author jmal
 * @Date 2020-06-16 16:24
 */
public class MyFileUtils {

    public static void main(String[] args) {
        File file = Paths.get("/Users/jmal/Downloads/data-center-1.0.log").toFile();

        File path = Paths.get("/Users/jmal/Downloads/data-center-1.0.log").getParent().getParent().toFile();
        for (String s : path.list()) {
            System.out.println(s);
        }
    }


    /***
     * 读取文件的前${lines}行
     * @param file
     * @param lines
     * @return
     */
    public static String readLines(File file,int lines){
        FileReader reader = new FileReader(file,StandardCharsets.UTF_8);
        return reader.read(new ReaderHandlerString(lines)).toString();
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



package com.jmal.clouddisk;
import cn.hutool.core.lang.Console;
import com.jmal.clouddisk.util.CompressUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

class Zip {
    public static void main(String[] args) throws IOException {
        long startTime = System.currentTimeMillis();
        List<Path> excludeFilePathList = Arrays.asList(Paths.get("/Users/jmal/Downloads/javafx/build"), Paths.get("/Users/jmal/Downloads/javafx/gradlew.bat"));
        CompressUtils.zip(Paths.get("/Users/jmal/Downloads/javafx"),excludeFilePathList,"/Users/jmal/Downloads/javafx.zip");
        Console.log(System.currentTimeMillis() - startTime);
    }
}

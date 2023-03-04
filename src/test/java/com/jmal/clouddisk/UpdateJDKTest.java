package com.jmal.clouddisk;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @Description updateJdk17Test
 * @blame jmal
 * @Date 2023/3/3 23:12
 */

@SpringBootTest
public class UpdateJDKTest {


    @Test
    public void moveFile() throws IOException {
        Path fromPath = Paths.get("/Users/jmal/Downloads/UU-macOS-2.7.0(237).dmg");
        Path outputFile = Paths.get("/Users/jmal/Downloads/move/");
        if (!Files.exists(outputFile)) {
            Files.createFile(outputFile);
        }
        PathUtil.move(fromPath, outputFile, true);
    }

    @Test
    public void copyFile() {
        File fromFile = Paths.get("/Users/jmal/Downloads/move/UU-macOS-2.7.0(237).dmg").toFile();
        File toFile = Paths.get("/Users/jmal/Downloads/数据列表excel6401e11321a0e1.07995309.xlsx").toFile();
        FileUtil.copy(fromFile, toFile, true);
    }

    @Test
    public void copyDir() {
        File fromFile = Paths.get("/Users/jmal/temp/filetest/rootpath/jmal/新建文件夹1/新建文件夹[]--/").toFile();
        File toFile = Paths.get("/Users/jmal/temp/filetest/rootpath/jmal/").toFile();
        FileUtil.copy(fromFile, toFile, true);
    }

}

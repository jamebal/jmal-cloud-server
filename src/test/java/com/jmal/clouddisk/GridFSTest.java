package com.jmal.clouddisk;

import cn.hutool.core.lang.Console;
import com.jmal.clouddisk.service.IFileVersionService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * @author jmal
 * @Description GridFSTest
 * @date 2023/5/10 17:47
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class GridFSTest {

    @Autowired
    IFileVersionService fileVersionService;

    @Test
    public void read() throws IOException {
        String fileId = "645b693fd03da37e89bb6ed8";
        try (InputStream inputStream = fileVersionService.readFileVersion(fileId);
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader bufferedReader = new BufferedReader(inputStreamReader);) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                Console.log(line);
            }
        }
    }
}

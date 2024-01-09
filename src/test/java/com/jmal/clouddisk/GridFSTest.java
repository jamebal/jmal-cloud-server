package com.jmal.clouddisk;

import cn.hutool.core.lang.Console;
import com.jmal.clouddisk.service.IFileVersionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
@SpringBootTest
class GridFSTest {

    @Autowired
    IFileVersionService fileVersionService;

    @Test
    void read() throws IOException {
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

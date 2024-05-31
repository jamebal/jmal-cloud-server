package com.jmal.clouddisk;

import cn.hutool.core.io.FileUtil;
import cn.hutool.crypto.SecureUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ClouddiskApplication
 *
 * @author jmal
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
public class ClouddiskApplication {

    public static void main(String[] args) {
        SecureUtil.disableBouncyCastle();
        SpringApplication application = new SpringApplication(ClouddiskApplication.class);
        // 允许循环引用
        application.setAllowCircularReferences(true);

        // dev环境下设置tesseract的lib路径
        Path tesseractLibPath = Paths.get("/opt/homebrew/Cellar/tesseract/5.3.4_1/lib");
        if (FileUtil.exist(tesseractLibPath.toFile())) {
            System.setProperty("jna.library.path", tesseractLibPath.toString());
        }

        application.run(args);
    }

}

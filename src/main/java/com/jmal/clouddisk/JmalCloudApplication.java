package com.jmal.clouddisk;

import cn.hutool.crypto.SecureUtil;
import com.jmal.clouddisk.util.TesseractUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ClouddiskApplication
 *
 * @author jmal
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
@Slf4j
public class JmalCloudApplication {

    public static void main(String[] args) {
        SecureUtil.disableBouncyCastle();
        SpringApplication application = new SpringApplication(JmalCloudApplication.class);
        // 允许循环引用
        application.setAllowCircularReferences(true);

        // dev环境下设置tesseract的lib路径
        TesseractUtil.setTesseractLibPath();

        application.run(args);
    }

}

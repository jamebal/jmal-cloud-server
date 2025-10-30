package com.jmal.clouddisk;

import cn.hutool.crypto.SecureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
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

@EnableMongoRepositories(basePackages = "com.jmal.clouddisk.dao.impl.mongodb")
@EnableJpaRepositories(basePackages = "com.jmal.clouddisk.dao.impl.jpa")
public class JmalCloudApplication {

    public static void main(String[] args) {
        SecureUtil.disableBouncyCastle();
        SpringApplication application = new SpringApplication(JmalCloudApplication.class);

        application.run(args);
    }

}

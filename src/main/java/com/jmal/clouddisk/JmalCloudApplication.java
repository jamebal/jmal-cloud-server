package com.jmal.clouddisk;

import cn.hutool.crypto.SecureUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
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
@EnableJpaRepositories(basePackages = "com.jmal.clouddisk.dao.repository.jpa")
@EnableMongoRepositories(basePackages = "com.jmal.clouddisk.dao.repository.mongo")
@ComponentScan(
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.jmal\\.clouddisk\\.dao\\.(repository|impl)\\..*"
        )
)
public class JmalCloudApplication {

    public static void main(String[] args) {
        SecureUtil.disableBouncyCastle();
        SpringApplication application = new SpringApplication(JmalCloudApplication.class);
        application.run(args);
    }

}

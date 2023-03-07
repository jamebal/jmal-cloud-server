package com.jmal.clouddisk;

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
public class ClouddiskApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ClouddiskApplication.class);
        // 允许循环引用
        application.setAllowCircularReferences(true);
        application.run(args);
    }

}

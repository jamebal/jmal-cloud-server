package com.jmal.clouddisk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

/**
 * ClouddiskApplication
 *
 * @blame jmal
 */
@SpringBootApplication
//启用缓存
@EnableCaching
public class ClouddiskApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClouddiskApplication.class, args);
    }

}

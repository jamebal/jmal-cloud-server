package com.jmal.clouddisk;

import com.apple.eawt.Application;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;

/**
 * ClouddiskApplication
 *
 * @author jmal
 */
@SpringBootApplication
@EnableCaching
public class ClouddiskApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(ClouddiskApplication.class);
        // 允许循环引用
        application.setAllowCircularReferences(true);
        application.run(args);
    }

}

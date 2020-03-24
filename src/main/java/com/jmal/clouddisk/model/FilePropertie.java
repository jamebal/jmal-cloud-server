package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.YamlPropertyLoaderFactory;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

/**
 * @Description 文件存储配置类
 * @Author jmal
 * @Date 2020-03-24 11:15
 */
@Data
@Component
@PropertySource(value = "classpath:file.yml", factory = YamlPropertyLoaderFactory.class)
@ConfigurationProperties(prefix = "file")
public class FilePropertie {
    private String rootDir = System.getProperty("user.dir");
    private String chunkFileDir = "chunkFileTemp";
    private String userImgDir = "/Image/";
    private String documentImgDir = "/Image/Document/Image/";
    private String separator = "/";
    /***
     * 文件监控扫描时间间隔(秒)
     */
    private Long timeInterval = 10l;
}

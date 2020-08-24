package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.YamlPropertyLoaderFactory;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;

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
    private String[] simText = {"txt","html","htm","asp","jsp","xml","json","properties","md","gitignore","java","py","c","cpp","sql","sh","bat","m","bas","prg","cmd"};
    private String[] doument = {"pdf","doc","docs","xls","xl","md"};

    private Boolean monitor = false;
    /***
     * 文件监控扫描时间间隔(秒)
     */
    private Long timeInterval = 10L;

    public String getRootDir(){
        return Paths.get(rootDir).toString();
    }

    public String getChunkFileDir() {
        return chunkFileDir;
    }

    public String getUserImgDir(){
        return userImgDir.replaceAll("/", File.separator);
    }

    public String getDocumentImgDir(){
        return documentImgDir.replaceAll("/", File.separator);
    }

//    public String[] getSimText(){
//        return this.simTextType.split("\\,");
//    }
//
//    public void setSimTextType(String simText){
//        System.out.println("setSimTextType");
//    }
}

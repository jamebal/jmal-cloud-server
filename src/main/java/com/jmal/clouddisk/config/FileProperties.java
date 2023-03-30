package com.jmal.clouddisk.config;

import cn.hutool.core.io.file.PathUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @Description 文件存储配置类
 * @Author jmal
 * @Date 2020-03-24 11:15
 */
@Data
@Component
@PropertySource(value = "classpath:file.yml", factory = YamlPropertyLoaderFactory.class)
@ConfigurationProperties(prefix = "file")
@Slf4j
public class FileProperties {
    /***
     * 文件存储根目录 文件监控目录
     */
    private String rootDir = System.getProperty("user.dir");
    /***
     * 断点续传的临时文件目录名称 位于rootDir下,文件监控扫描忽略的目录
     */
    private String chunkFileDir = "chunkFileTemp";
    /***
     * 用户头像默认存储路径
     */
    private String userImgDir = "/Image/usr/";
    /***
     * markdown类型文件(文章)默认存储的位置
     */
    private String documentDir = "/Document/";
    /***
     * markdown类型文件(文章)里图片默认存储的位置
     */
    private String documentImgDir = "/Image/Document/";
    /**
     * FTP server 端口号
     */
    private Integer ftpServerPort = 8089;
    /***
     * 默认分隔符
     */
    private String separator = "/";
    /***
     * 文本编辑器支持的文本类型
     */
    private String[] simText = {"txt", "html", "htm", "asp", "jsp", "xml", "json", "properties", "md", "gitignore", "java", "py", "c", "cpp", "sql", "sh", "bat", "m", "bas", "prg", "cmd"};
    /***
     * 文档类型
     */
    private String[] document = {"pdf", "doc", "docs", "xls", "xl", "md"};
    /***
     * 是否开启文件监控(默认开启)
     * 开启文件监控会监控 ${rootDir} 目录下文件的变化
     */
    private Boolean monitor = true;
    /***
     * 文件监控扫描时间间隔(秒)
     */
    private Long timeInterval = 10L;
    /***
     * webDAV协议前缀
     */
    private String webDavPrefix;
    /***
     * ip2region-path
     */
    private String ip2regionDbPath;

    public void setIp2regionDbPath(String path) {
        Path dbPath = Paths.get(path);
        if (!PathUtil.exists(dbPath, true)) {
            log.error("Error: Invalid ip2region.xdb file");
            ip2regionDbPath = null;
            return;
        }
        this.ip2regionDbPath = path;
    }

    public String getIp2regionDbPath() {
        return ip2regionDbPath;
    }

    public String getWebDavPrefixPath() {
        return "/" + webDavPrefix;
    }

    public String getRootDir() {
        return Paths.get(rootDir).toString();
    }

    public String getChunkFileDir() {
        return chunkFileDir;
    }

    public String getUserImgDir() {
        return Paths.get(userImgDir).toString();
    }

    public String getDocumentImgDir() {
        return Paths.get(documentImgDir).toString();
    }

    public String getDocumentDir() {
        return Paths.get(documentDir).toString();
    }


}

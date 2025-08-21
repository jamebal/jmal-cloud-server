package com.jmal.clouddisk.config;

import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.lucene.LuceneService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    private String testTempDir = System.getProperty("user.dir") + "/testTempDir";
    /***
     * 文件存储根目录 文件监控目录
     */
    private String rootDir = System.getProperty("user.dir");
    /***
     * 断点续传的临时文件目录名称 位于rootDir下,文件监控扫描忽略的目录
     */
    private String chunkFileDir = "chunkFileTemp";
    /**
     * 视频转码后的缓存目录, 位于 ${chunkFileDir}/${username}/${videoTranscodeCache}
     */
    private String videoTranscodeCache = "videoTranscodeCache";
    /**
     * 回收站目录, 位于 ${chunkFileDir}/${username}/${jmalcloudTrashDir}
     */
    private String jmalcloudTrashDir = ".jmalcloudTrash";
    /**
     * lucene索引存储目录
     */
    private String luceneIndexDir = "luceneIndex";
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
    private List<String> simText = new ArrayList<>();
    /***
     * 文档类型
     */
    private List<String> document = new ArrayList<>();
    /***
     * 是否开启文件监控(默认开启)
     * 开启文件监控会监控 ${rootDir} 目录下文件的变化
     */
    private Boolean monitor = true;
    /***
     * 文件监控扫描时间间隔(秒)
     */
    private Long timeInterval = 10L;
    /**
     * 文件监控忽略的文件前缀
     */
    private List<String> monitorIgnoreFilePrefix;
    /**
     * webDAV协议前缀
     */
    private String webDavPrefix;
    /***
     * ip2region-path
     */
    private String ip2regionDbPath;

    /**
     * ocr-lite-onnx模型路径
     */
    private String ocrLiteONNXModelPath;

    /**
     * 是否开启精确搜索
     */
    private Boolean exactSearch = false;

    /**
     * ngram最大内容长度
     */
    private Double ngramMaxContentLengthMB = 5.0;
    /**
     * ngram最小长度
     */
    private Integer ngramMinSize = 2;
    /**
     * ngram最大长度
     */
    private Integer ngramMaxSize = 6;

    public void setIp2regionDbPath(String path) {
        Path dbPath = Paths.get(path);
        if (!PathUtil.exists(dbPath, true)) {
            log.error("Error: Invalid ip2region.xdb file");
            ip2regionDbPath = null;
            return;
        }
        this.ip2regionDbPath = path;
    }

    private static final long GIGABYTE = 1024L * 1024 * 1024;
    private static final double NGRAM_THRESHOLD_MB = 3.0;
    private static final int MAX_MEMORY_GB = 4;
    private static final double ONE_GB_CONFIG = 0.1;
    private static final double TWO_GB_CONFIG = 1.0;
    private static final double THREE_GB_CONFIG = 2.0;

    public int getNgramMaxContentLength() {
        double effectiveNgramMaxContentLengthMB = determineEffectiveNGramLength();
        return (int) (effectiveNgramMaxContentLengthMB * LuceneService.BYTES_PER_MB);
    }

    private double determineEffectiveNGramLength() {
        // 当可用内存小于4G，需要设置更小的ngramMaxContentLengthMB
        long maxMemory = Runtime.getRuntime().maxMemory();
        double effectiveNgramMaxContentLengthMB = this.ngramMaxContentLengthMB;
        if (maxMemory < MAX_MEMORY_GB * GIGABYTE && effectiveNgramMaxContentLengthMB >= NGRAM_THRESHOLD_MB) {
            if (maxMemory < GIGABYTE) {
                effectiveNgramMaxContentLengthMB = ONE_GB_CONFIG;
            } else if (maxMemory < 2 * GIGABYTE) {
                effectiveNgramMaxContentLengthMB = TWO_GB_CONFIG;
            } else {
                effectiveNgramMaxContentLengthMB = THREE_GB_CONFIG;
            }
        }
        return effectiveNgramMaxContentLengthMB;
    }

    public void setMonitorIgnoreFilePrefix(String monitorIgnoreFilePrefix) {
        if (monitorIgnoreFilePrefix != null && !monitorIgnoreFilePrefix.isEmpty()) {
            this.monitorIgnoreFilePrefix = Arrays.asList(monitorIgnoreFilePrefix.split(","));
        } else {
            this.monitorIgnoreFilePrefix = new ArrayList<>();
        }
    }

    public String getWebDavPrefixPath() {
        return "/" + webDavPrefix;
    }

    public String getRootDir() {
        return Paths.get(rootDir).toString();
    }

    public Path getTestTempDirPath() {
        return Paths.get(testTempDir);
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

    /**
     * 判断用户名是否不允许使用
     *
     * @param username 用户名
     * @return true: 不允许使用
     */
    public boolean notAllowUsername(String username) {
        if (StrUtil.isBlank(username)) {
            return false;
        }
        if (getChunkFileDir().equals(username)) {
            return true;
        }
        if (getLuceneIndexDir().equals(username)) {
            return true;
        }
        return username.startsWith("log-");
    }


}

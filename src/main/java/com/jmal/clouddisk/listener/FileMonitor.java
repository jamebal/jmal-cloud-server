package com.jmal.clouddisk.listener;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.HeartwingsDO;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.util.SystemUtil;
import io.methvin.watcher.DirectoryWatcher;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.data.mongodb.core.index.IndexResolver;
import org.springframework.data.mongodb.core.index.MongoPersistentEntityIndexResolver;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * @Description 启动时开启文件目录监控
 * @Date 2020-02-21 14:54
 * @author jmal
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class FileMonitor {

    final FileProperties fileProperties;

    final MongoTemplate mongoTemplate;

    final IFileService fileService;

    final FileListener fileListener;

    @Value("${version}")
    private String version;

    private String newVersion = null;

    private DirectoryWatcher watcher;

    @EventListener(ContextRefreshedEvent.class)
    public void initIndicesAfterStartup() {

        MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext = mongoTemplate
                .getConverter().getMappingContext();

        IndexResolver resolver = new MongoPersistentEntityIndexResolver(mappingContext);

        // 创建fileDocument索引
        IndexOperations fileDocument = mongoTemplate.indexOps(FileDocument.class);
        resolver.resolveIndexFor(FileDocument.class).forEach(fileDocument::ensureIndex);
        // 创建log索引
        IndexOperations log = mongoTemplate.indexOps(LogOperation.class);
        resolver.resolveIndexFor(LogOperation.class).forEach(log::ensureIndex);
        // 创建heartwings索引
        IndexOperations heartwings = mongoTemplate.indexOps(HeartwingsDO.class);
        resolver.resolveIndexFor(HeartwingsDO.class).forEach(heartwings::ensureIndex);
    }

    @PostConstruct
    public void init() throws Exception {
        // 检测新版本
        newVersion = SystemUtil.getNewVersion();
        // 判断是否开启文件监控
        if (Boolean.FALSE.equals(fileProperties.getMonitor())) {
            return;
        }
        // 忽略目录
        fileListener.addFilterDir(Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir()));
        fileListener.addFilterDir(Paths.get(fileProperties.getRootDir(), fileProperties.getLuceneIndexDir()));

        // 开启文件监控
        newDirectoryWatcher();
    }

    private void newDirectoryWatcher() throws IOException {
        Path rootDir = Paths.get(fileProperties.getRootDir());
        PathUtil.mkdir(rootDir);
        this.watcher = DirectoryWatcher.builder()
                .path(rootDir)
                .listener(fileListener)
                .logger(log)
                .fileHashing(false)
                .build();
        watcher.watchAsync();
        log.info("\r\n文件监控服务已开启, 监控目录: {}, 忽略目录: {}", rootDir, fileListener.getFilterDirSet());
    }

    private void reloadDirectoryWatcher() {
        try {
            watcher.close();
            newDirectoryWatcher();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 在过滤器里添加路径
     * @param path 需要过滤掉的路径
     */
    public void addDirFilter(String path) {
        Path filepath = Paths.get(fileProperties.getRootDir(), path);
        fileListener.addFilterDir(filepath);
        String username = Paths.get(path).getParent().getFileName().toString();
        fileService.createFile(username, filepath.toFile());
        reloadDirectoryWatcher();
    }

    /**
     * 需要移除的路径
     * @param path 需要移除的路径
     */
    public void removeDirFilter(String path) {
        Path filepath = Paths.get(fileProperties.getRootDir(), path);
        if (fileListener.containsFilterDir(filepath)) {
            fileListener.removeFilterDir(filepath);
            String username = Paths.get(path).getParent().getFileName().toString();
            fileService.deleteFile(username, Paths.get(fileProperties.getRootDir(), path).toFile());
            reloadDirectoryWatcher();
        }
    }

    /**
     * <p>定时清理临时目录</p>
     * 每天凌晨2点执行<br>
     * 1.清理临时目录中3天前的文件<br>
     * 2.清理视频转码缓存目录中的无效文件<br>
     * 3.清理回收站中30天前的文件<br>
     */
    @Scheduled(cron = "0 0 2 * * ?")
    private void cleanTempDir() {
        // 临时目录
        Path tempPath = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir());
        for (File username : FileUtil.ls(tempPath.toString())) {
            if (username.isDirectory()) {
                for (File file : FileUtil.ls(username.getAbsolutePath())) {
                    // 是否为三天前的文件
                    boolean sevenDayAgo = file.lastModified() < (System.currentTimeMillis() - DateUnit.DAY.getMillis() * 3);
                    // 是否为视频转码缓存目录
                    boolean videoCache = fileProperties.getVideoTranscodeCache().equals(file.getName()) || fileProperties.getLuceneIndexDir().equals(file.getParentFile().getName());
                    if (videoCache) {
                        if (file.listFiles() != null) {
                            for (File f : Objects.requireNonNull(file.listFiles())) {
                                FileDocument fileDocument = fileService.getById(f.getName());
                                if (fileDocument == null || f.isFile()) {
                                    FileUtil.del(f);
                                    log.info("删除视频转码缓存文件: {}", f.getAbsolutePath());
                                }
                            }
                        }
                        return;
                    }
                    // 回收站
                    boolean trash = fileProperties.getJmalcloudTrashDir().equals(file.getName());
                    if (trash) {
                        return;
                    }
                    if (sevenDayAgo) {
                        FileUtil.del(file);
                    }
                }
            }
        }
    }

    public String hasNewVersion() {
        if (StrUtil.isBlank(newVersion)) {
            return null;
        }
        // 判断是否有新版本, 比较newVersion和version
        if (newVersion.compareTo("v" + version) > 0) {
            return newVersion;
        }
        return null;
    }

    /**
     * 每3小时检查一次版本
     */
    @Scheduled(cron = "0 0 0/3 * * ?")
    private void getNewVersion() {
        newVersion = SystemUtil.getNewVersion();
    }
}

package com.jmal.clouddisk.listener;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.util.SystemUtil;
import io.methvin.watcher.DirectoryWatcher;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
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

    final IFileService fileService;

    private final CommonFileService commonFileService;

    final FileListener fileListener;

    @Value("${version}")
    private String version;

    private String newVersion = null;

    private DirectoryWatcher watcher;

    // 这个锁专门用于保护 'watcher' 实例的生命周期（创建、关闭、重载）
    private final Object watcherLock = new Object();

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationReady(ContextRefreshedEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        ThreadUtil.execute(this::init);
    }

    public void init() {
        // 检查新版本
        // 启动文件监控服务
        startFileMonitoringAsync();
        Completable.fromAction(() -> {
            String newVersion = SystemUtil.getNewVersion();
            log.debug("Current version: {}, Latest version: {}", version, newVersion);
        }).subscribeOn(Schedulers.io())
                .subscribe();
    }

    /**
     * <p>启动文件监控服务</p>
     */
    public void startFileMonitoringAsync() {
        // 判断是否开启文件监控
        if (!BooleanUtil.isTrue(fileProperties.getMonitor())) {
            return;
        }
        Completable.fromAction(() -> {
            synchronized (watcherLock) {
                try {
                    // 如果已经存在一个watcher (可能是重载逻辑调用)，先安全关闭
                    if (this.watcher != null) {
                        this.watcher.close();
                    }
                    // 忽略目录
                    fileListener.addFilterDir(Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir()));
                    fileListener.addFilterDir(Paths.get(fileProperties.getRootDir(), fileProperties.getLuceneIndexDir()));
                    fileListener.addFilterDir(Paths.get(fileProperties.getRootDir(), fileProperties.getJmalcloudDBDir()));
                    // 开启文件监控
                    newDirectoryWatcher();
                } catch (IOException e) {
                    log.error("文件监控服务启动失败: {}", e.getMessage());
                }
            }
        }).subscribeOn(Schedulers.io())
                .subscribe();
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

    /**
     * 在过滤器里添加路径
     * @param path 需要过滤掉的路径
     */
    public void addDirFilter(String path) {
        Path filepath = Paths.get(fileProperties.getRootDir(), path);
        fileListener.addFilterDir(filepath);
        String username = Paths.get(path).getParent().getFileName().toString();
        fileService.createFile(username, filepath.toFile());
    }

    /**
     * 需要移除的路径
     * @param path 需要移除的路径
     */
    public synchronized void removeDirFilter(String path) {
        Path filepath = Paths.get(fileProperties.getRootDir(), path);
        if (fileListener.containsFilterDir(filepath)) {
            fileListener.removeFilterDir(filepath);
            String username = Paths.get(path).getParent().getFileName().toString();
            fileService.deleteFile(username, Paths.get(fileProperties.getRootDir(), path).toFile());
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
                    clearUserCache(file);
                }
            }
        }
    }

    /**
     * 清理用户缓存文件
     * @param file 需要清理的文件
     */
    private void clearUserCache(File file) {
        // 是否为三天前的文件
        boolean sevenDayAgo = file.lastModified() < (System.currentTimeMillis() - DateUnit.DAY.getMillis() * 3);
        // 是否为视频转码缓存目录
        boolean videoCache = fileProperties.getVideoTranscodeCache().equals(file.getName()) || fileProperties.getLuceneIndexDir().equals(file.getParentFile().getName());
        // 回收站
        boolean trash = fileProperties.getJmalcloudTrashDir().equals(file.getName());
        if (videoCache || trash) {
            clearVideoCache(file, videoCache);
            return;
        }
        if (sevenDayAgo) {
            FileUtil.del(file);
        }
    }

    /**
     * 清理视频转码缓存目录
     * @param file 需要清理的文件
     * @param videoCache 是否为视频转码缓存目录
     */
    private void clearVideoCache(File file, boolean videoCache) {
        if (videoCache && file.listFiles() != null) {
            for (File f : Objects.requireNonNull(file.listFiles())) {
                FileDocument fileDocument = commonFileService.getById(f.getName());
                if (fileDocument == null || f.isFile()) {
                    FileUtil.del(f);
                    log.info("删除视频转码缓存文件: {}", f.getAbsolutePath());
                }
            }
        }
    }

    public String hasNewVersion() {
        if (CharSequenceUtil.isBlank(newVersion)) {
            return null;
        }
        // 判断是否有新版本, 比较newVersion和version
        if (newVersion.compareTo("v" + version) > 0) {
            return newVersion;
        }
        return null;
    }

    /**
     * 每12小时检查一次版本
     */
    @Scheduled(cron = "0 0 0/12 * * ?")
    private void getNewVersion() {
        newVersion = SystemUtil.getNewVersion();
    }

    /**
     * 在应用关闭时，优雅地关闭文件监控。
     */
    @PreDestroy
    public void cleanup() {
        synchronized (watcherLock) {
            if (this.watcher != null) {
                try {
                    this.watcher.close();
                } catch (IOException e) {
                    log.error("Error closing file watcher.", e);
                }
            }
        }
    }
}

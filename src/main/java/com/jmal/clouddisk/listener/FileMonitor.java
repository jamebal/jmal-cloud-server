package com.jmal.clouddisk.listener;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.io.FileUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.MongodbIndex;
import com.jmal.clouddisk.util.CaffeineUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

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

    final FileListener fileListener;

    final MongodbIndex mongodbIndex;

    final IFileService fileService;

    private FileAlterationMonitor monitor;

    private FileAlterationObserver observer;

    private boolean isMonitor = false;

    /**
     * 续要过滤掉的目录列表
     */
    private static final Set<String> FILTER_DIR_SET = new CopyOnWriteArraySet<>();

    @PostConstruct
    public void init() throws Exception {
        // 判断是否开启文件监控
        if (Boolean.FALSE.equals(fileProperties.getMonitor())) {
            return;
        }
        Path rootDir = Paths.get(fileProperties.getRootDir());
        if (!Files.exists(rootDir)) {
            Files.createDirectories(rootDir);
        }
        // 轮询间隔(秒)
        long interval = TimeUnit.SECONDS.toMillis(fileProperties.getTimeInterval());
        FILTER_DIR_SET.add(fileProperties.getChunkFileDir());
        newObserver();
        //创建文件变化监听器
        monitor = new FileAlterationMonitor(interval, observer);
        // 开始监控
        monitor.start();
        isMonitor = true;
        log.info("\r\n文件监控服务已开启:\r\n轮询间隔:{}秒\n监控目录:{}\n忽略目录:{}", fileProperties.getTimeInterval(), rootDir, rootDir + File.separator + fileProperties.getChunkFileDir());
        // 检测mongo索引
        mongodbIndex.checkMongoIndex();
    }

    private void newObserver() {
        // 创建过滤器
        TempDirFilter tempDirFilter = new TempDirFilter(fileProperties.getRootDir(), FILTER_DIR_SET);
        // 使用过滤器
        observer = new FileAlterationObserver(new File(fileProperties.getRootDir()), tempDirFilter);
        observer.addListener(fileListener);
    }

    private void reloadObserver() {
        try {
            newObserver();
            fastInterval();
            log.info("reload FileMonitor, filterDir: {}", FILTER_DIR_SET);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    /**
     * 在过滤器里添加路径
     * @param path 需要过滤掉的路径
     */
    public void addDirFilter(String path) {
        FILTER_DIR_SET.add(path);
        String username = Paths.get(path).getParent().getFileName().toString();
        fileService.createFile(username, Paths.get(fileProperties.getRootDir(), path).toFile());
        reloadObserver();
    }

    /**
     * 需要移除的路径
     * @param path 需要移除的路径
     */
    public void removeDirFilter(String path) {
        if (FILTER_DIR_SET.contains(path)) {
            FILTER_DIR_SET.remove(path);
            String username = Paths.get(path).getParent().getFileName().toString();
            fileService.deleteFile(username, Paths.get(fileProperties.getRootDir(), path).toFile());
            reloadObserver();
        }
    }

    /**
     * 5分钟没人访问时，降低轮询频率
     */
    @Scheduled(fixedDelay = 1000, initialDelay = 5000)
    private void check() {
        long diff = System.currentTimeMillis() - CaffeineUtil.getLastAccessTimeCache();
        try {
            if (diff > DateUnit.MINUTE.getMillis() * 5) {
                if (isMonitor) {
                    monitor.stop(DateUnit.SECOND.getMillis());
                    monitor = null;
                    monitor = new FileAlterationMonitor(DateUnit.MINUTE.getMillis() * 30, observer);
                    monitor.start();
                    log.info("轮询间隔改为30分钟");
                    isMonitor = false;
                }
            } else {
                if (!isMonitor) {
                    fastInterval();
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private void fastInterval() throws Exception {
        monitor.stop(DateUnit.SECOND.getMillis());
        monitor = null;
        monitor = new FileAlterationMonitor(DateUnit.SECOND.getMillis() * 3, observer);
        monitor.start();
        log.info("轮询间隔改为3秒钟");
        isMonitor = true;
    }

    /**
     * 定时清理临时目录
     * 每天凌晨2点执行
     * 清理临时目录中7天前的文件
     */
    @Scheduled(cron = "0 0 2 * * ?")
    private void cleanTempDir() {
        // 临时目录
        Path tempPath = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir());
        for (File username : FileUtil.ls(tempPath.toString())) {
            if (username.isDirectory()) {
                for (File file : FileUtil.ls(username.getAbsolutePath())) {
                    if (file.lastModified() < (System.currentTimeMillis() - DateUnit.DAY.getMillis() * 7)) {
                        FileUtil.del(file);
                    }
                }
            }
        }
    }
}

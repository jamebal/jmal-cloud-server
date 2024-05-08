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
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.SystemUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
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

    final MongoTemplate mongoTemplate;

    final IFileService fileService;

    private FileAlterationMonitor monitor;

    private FileAlterationObserver observer;

    private boolean isMonitor = false;

    @Value("${version}")
    private String version;

    private String newVersion = null;

    /**
     * 续要过滤掉的目录列表
     */
    private static final Set<String> FILTER_DIR_SET = new CopyOnWriteArraySet<>();

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
        Path rootDir = Paths.get(fileProperties.getRootDir());
        PathUtil.mkdir(rootDir);
        // 轮询间隔(秒)
        long interval = TimeUnit.SECONDS.toMillis(fileProperties.getTimeInterval());
        FILTER_DIR_SET.add(fileProperties.getChunkFileDir());
        FILTER_DIR_SET.add(fileProperties.getLuceneIndexDir());
        newObserver();
        //创建文件变化监听器
        monitor = new FileAlterationMonitor(interval, observer);
        // 开始监控
        monitor.start();
        isMonitor = true;
        log.info("\r\n文件监控服务已开启:\r\n轮询间隔:{}秒\n监控目录:{}\n忽略目录:{}", fileProperties.getTimeInterval(), rootDir, FILTER_DIR_SET);
    }

    private void newObserver() {
        // 创建过滤器
        TempDirFilter tempDirFilter = new TempDirFilter(fileProperties.getRootDir(), FILTER_DIR_SET);
        // 使用过滤器
        observer = new FileAlterationObserver(new File(fileProperties.getRootDir()), tempDirFilter);
        observer.addListener(fileListener);
    }

    private void reloadObserver() {
        if (monitor == null) {
            return;
        }
        try {
            newObserver();
            fastInterval();
            log.info("reload FileMonitor, ignoreDir: {}", FILTER_DIR_SET);
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
        if (monitor == null) {
            return;
        }
        long diff = System.currentTimeMillis() - CaffeineUtil.getLastAccessTimeCache();
        try {
            if (diff > DateUnit.MINUTE.getMillis() * 5) {
                slowlyInterval();
            } else {
                if (!isMonitor) {
                    fastInterval();
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private void slowlyInterval() throws Exception {
        if (monitor == null) {
            return;
        }
        if (isMonitor) {
            monitor.stop(DateUnit.SECOND.getMillis());
            monitor = null;
            monitor = new FileAlterationMonitor(DateUnit.MINUTE.getMillis() * 30, observer);
            monitor.start();
            log.info("轮询间隔改为3分钟");
            isMonitor = false;
        }
    }

    private void fastInterval() throws Exception {
        if (monitor == null) {
            return;
        }
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
                    // 是否为七天前的文件
                    boolean sevenDayAgo = file.lastModified() < (System.currentTimeMillis() - DateUnit.DAY.getMillis() * 7);
                    // 是否为视频转码缓存目录
                    boolean videoCache = fileProperties.getVideoTranscodeCache().equals(file.getName()) || fileProperties.getLuceneIndexDir().equals(file.getParentFile().getName());
                    if (sevenDayAgo && !videoCache) {
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

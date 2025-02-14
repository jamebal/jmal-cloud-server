package com.jmal.clouddisk.listener;

import cn.hutool.core.text.CharSequenceUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.service.IFileService;
import io.methvin.watcher.DirectoryChangeEvent;
import io.methvin.watcher.DirectoryChangeListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileListener implements DirectoryChangeListener {

    private final FileProperties fileProperties;
    private final IFileService fileService;

    /**
     * 需要过滤掉的目录列表
     */
    @Getter
    private final Set<Path> filterDirSet = new CopyOnWriteArraySet<>();

    private final Cache<Path, DirectoryChangeEvent> eventCache = Caffeine.newBuilder()
            .expireAfterWrite(100, TimeUnit.MILLISECONDS)
            .maximumSize(100000)
            .build();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    @PostConstruct
    public void init() {
        // 定期处理缓存中的事件
        scheduler.scheduleAtFixedRate(this::processCache, 100, 100, TimeUnit.MILLISECONDS);
    }

    private void processCache() {
        eventCache.asMap().values().forEach(event -> {
            try {
                processEvent(event);
            } catch (Exception e) {
                log.error("Error processing event", e);
            }
        });
        eventCache.cleanUp();
    }

    public void addFilterDir(Path path) {
        filterDirSet.add(path);
    }

    public void removeFilterDir(Path path) {
        filterDirSet.remove(path);
    }

    public boolean containsFilterDir(Path path) {
        return filterDirSet.contains(path);
    }

    @Override
    public void onEvent(DirectoryChangeEvent directoryChangeEvent) {
        Path eventPath = directoryChangeEvent.path();
        DirectoryChangeEvent.EventType eventType = directoryChangeEvent.eventType();

        // 检查是否为忽略路径
        if (filterDirSet.stream().anyMatch(eventPath::startsWith)) {
            log.debug("Ignore Event: {}, Path: {}", eventType, eventPath);
            return;
        }

        if (fileProperties.getMonitorIgnoreFilePrefix().stream().anyMatch(eventPath.getFileName()::startsWith)) {
            log.debug("忽略文件:{}", eventPath.toFile().getAbsolutePath());
            return;
        }

        log.debug("Received Event: {}, Path: {}", eventType, eventPath);
        eventCache.put(eventPath, directoryChangeEvent);
    }

    private void processEvent(DirectoryChangeEvent directoryChangeEvent) {
        Path eventPath = directoryChangeEvent.path();
        DirectoryChangeEvent.EventType eventType = directoryChangeEvent.eventType();
        File file = eventPath.toFile();
        switch (eventType) {
            case CREATE:
                onFileCreate(file);
                break;
            case MODIFY:
                onFileChange(file);
                break;
            case DELETE:
                onFileDelete(file);
                break;
            default:
                break;
        }
    }

    /**
     * 文件创建执行
     *
     * @param file 文件
     */
    public void onFileCreate(File file) {
        try {
            String username = ownerOfChangeFile(file);
            if (CharSequenceUtil.isBlank(username)) {
                return;
            }
            fileService.createFile(username, file);
            log.info("用户:{},新建文件:{}", username, file.getAbsolutePath());
        } catch (Exception e) {
            log.error("新建文件后续操作失败, {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * 文件创建修改
     *
     * @param file 文件
     */
    public void onFileChange(File file) {
        try {
            String username = ownerOfChangeFile(file);
            if (CharSequenceUtil.isBlank(username)) {
                return;
            }
            fileService.updateFile(username, file);
            log.info("用户:{},修改文件:{}", username, file.getAbsolutePath());
        } catch (Exception e) {
            log.error("修改文件后续操作失败, fileAbsolutePath: {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * 文件删除
     *
     * @param file 文件
     */
    public void onFileDelete(File file) {
        try {
            String username = ownerOfChangeFile(file);
            if (CharSequenceUtil.isBlank(username)) {
                return;
            }
            fileService.deleteFile(username, file);
            log.info("用户:{},删除文件:{}", username, file.getAbsolutePath());
        } catch (Exception e) {
            log.error("删除文件后续操作失败, fileAbsolutePath: {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * 判断变化的文件属于哪个用户
     *
     * @return username
     */
    private String ownerOfChangeFile(File file) {
        String username = null;
        try {
            int rootPathCount = Paths.get(fileProperties.getRootDir()).getNameCount();
            if (file.toPath().getNameCount() == rootPathCount + 1) {
                return null;
            }
            username = file.toPath().subpath(rootPathCount, rootPathCount + 1).toString();
        } catch (Exception e) {
            log.error("解析路径失败, fileAbsolutePath: {}", file.getAbsolutePath(), e);
        }
        return username;
    }

    @Override
    public boolean isWatching() {
        return DirectoryChangeListener.super.isWatching();
    }

    @Override
    public void onIdle(int i) {
        DirectoryChangeListener.super.onIdle(i);
    }

    @Override
    public void onException(Exception e) {
        DirectoryChangeListener.super.onException(e);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}

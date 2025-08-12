package com.jmal.clouddisk.listener;

import cn.hutool.core.text.CharSequenceUtil;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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

    // 使用ConcurrentHashMap存储最新事件，确保每个路径只有一个最新事件
    private final Map<Path, DirectoryChangeEvent> eventMap = new ConcurrentHashMap<>();

    // 处理队列 - 用于实际处理事件的工作队列
    private final BlockingQueue<DirectoryChangeEvent> processingQueue = new LinkedBlockingQueue<>(100000);

    // 重试队列
    private final BlockingQueue<DirectoryChangeEvent> retryQueue = new LinkedBlockingQueue<>();

    // 统计
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final AtomicInteger mergedCount = new AtomicInteger(0);
    private final AtomicInteger retryCount = new AtomicInteger(0);

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
        Thread t = new Thread(r, "event-scheduler-thread");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService processExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> {
                Thread t = new Thread(r, "file-process-thread");
                t.setDaemon(true);
                return t;
            });

    @PostConstruct
    public void init() {
        // 定期将事件从Map转移到处理队列
        scheduler.scheduleAtFixedRate(this::transferEventsToQueue, 200, 200, TimeUnit.MILLISECONDS);

        // 启动消费者线程
        startConsumerThreads();

        // 启动重试线程
        startRetryThread();

        // 定期输出统计信息
        startStatsReporter();
    }

    private void transferEventsToQueue() {
        if (eventMap.isEmpty()) {
            return;
        }
        // 快照当前事件Map并清空
        Map<Path, DirectoryChangeEvent> currentEvents = new ConcurrentHashMap<>(eventMap);
        eventMap.clear();

        // 转移到处理队列
        int transferCount = 0;
        for (DirectoryChangeEvent event : currentEvents.values()) {
            boolean offered = processingQueue.offer(event);
            if (!offered) {
                log.error("处理队列已满，无法添加事件: {}, 路径: {}", event.eventType(), event.path());
                // 放回原Map以便下次尝试
                eventMap.put(event.path(), event);
            } else {
                transferCount++;
            }
        }

        if (transferCount > 0) {
            log.debug("转移了{}个事件到处理队列，当前队列大小: {}", transferCount, processingQueue.size());
        }
    }

    private void startConsumerThreads() {
        int processors = Runtime.getRuntime().availableProcessors();
        for (int i = 0; i < processors; i++) {
            processExecutor.submit(this::processQueueItems);
        }
    }

    private void processQueueItems() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                DirectoryChangeEvent event = processingQueue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    try {
                        processEvent(event);
                        processedCount.incrementAndGet();
                    } catch (Exception e) {
                        log.error("处理事件失败: {}, 路径: {}", event.eventType(), event.path(), e);
                        retryQueue.offer(event);
                        failedCount.incrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void startRetryThread() {
        Thread retryThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    DirectoryChangeEvent event = retryQueue.poll(500, TimeUnit.MILLISECONDS);
                    if (event != null) {
                        try {
                            log.info("重试处理文件事件: {}, 路径: {}", event.eventType(), event.path());
                            processEvent(event);
                            processedCount.incrementAndGet();
                            retryCount.incrementAndGet();
                        } catch (Exception e) {
                            log.error("重试处理文件事件失败: {}, 路径: {}", event.eventType(), event.path(), e);
                            // 等待一段时间后再次加入重试队列
                            Thread.sleep(1000);
                            retryQueue.offer(event);
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "file-retry-thread");
        retryThread.setDaemon(true);
        retryThread.start();
    }

    private void startStatsReporter() {
        scheduler.scheduleAtFixedRate(() -> {
            log.info("文件处理统计 - 已处理: {}, 失败: {}, 合并: {}, 重试成功: {}, 队列积压: {}, 重试队列: {}, 事件Map: {}",
                    processedCount.get(), failedCount.get(), mergedCount.get(), retryCount.get(),
                    processingQueue.size(), retryQueue.size(), eventMap.size());
        }, 30, 30, TimeUnit.SECONDS);
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
            log.debug("忽略事件: {}, 路径: {}", eventType, eventPath);
            return;
        }

        if (fileProperties.getMonitorIgnoreFilePrefix().stream().anyMatch(eventPath.getFileName()::startsWith)) {
            log.debug("忽略文件:{}", eventPath.toFile().getAbsolutePath());
            return;
        }

        log.warn("接收到事件: {}, 路径: {}", eventType, eventPath);

        // 智能事件合并
        DirectoryChangeEvent previousEvent = eventMap.get(eventPath);
        if (previousEvent != null) {
            // CREATE + MODIFY = CREATE (优化为一次创建)
            if (previousEvent.eventType() == DirectoryChangeEvent.EventType.CREATE &&
                    directoryChangeEvent.eventType() == DirectoryChangeEvent.EventType.MODIFY) {
                log.debug("优化事件: 创建+修改合并为创建, 路径: {}", eventPath);
                eventMap.put(eventPath, directoryChangeEvent);
                mergedCount.incrementAndGet();
                return;
            }

            // 多次MODIFY = 最后一次MODIFY
            if (previousEvent.eventType() == DirectoryChangeEvent.EventType.MODIFY &&
                    directoryChangeEvent.eventType() == DirectoryChangeEvent.EventType.MODIFY) {
                log.debug("优化事件: 合并多次修改, 路径: {}", eventPath);
                eventMap.put(eventPath, directoryChangeEvent);
                mergedCount.incrementAndGet();
                return;
            }

            // DELETE会覆盖之前的任何事件
            if (directoryChangeEvent.eventType() == DirectoryChangeEvent.EventType.DELETE) {
                log.debug("优化事件: 删除事件覆盖之前的事件, 路径: {}", eventPath);
                eventMap.put(eventPath, directoryChangeEvent);
                mergedCount.incrementAndGet();
                return;
            }

            mergedCount.incrementAndGet();
        }

        // 将事件放入Map
        eventMap.put(eventPath, directoryChangeEvent);
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
            throw e; // 重新抛出异常以触发重试机制
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
            throw e; // 重新抛出异常以触发重试机制
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
            throw e; // 重新抛出异常以触发重试机制
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
        scheduler.shutdown();
        processExecutor.shutdown();
        try {
            // 等待所有任务完成
            if (!processExecutor.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("线程池未能在60秒内完全关闭，剩余任务数: {}", processingQueue.size() + retryQueue.size());
                processExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            processExecutor.shutdownNow();
        }
    }
}

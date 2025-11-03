package com.jmal.clouddisk.listener;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.lucene.RebuildIndexTaskService;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileListener implements DirectoryChangeListener {

    private final FileProperties fileProperties;
    private final IFileService fileService;
    private final RebuildIndexTaskService rebuildIndexTaskService;

    /**
     * 需要过滤掉的目录列表
     */
    @Getter
    private final Set<Path> filterDirSet = new CopyOnWriteArraySet<>();

    private static final Duration GRACE_PERIOD = Duration.ofSeconds(2); // 2秒的静默期

    // 使用ConcurrentHashMap存储最新事件，确保每个路径只有一个最新事件
    private final Map<Path, EventWrapper> eventMap = new ConcurrentHashMap<>();

    // 处理队列 - 用于实际处理事件的工作队列
    private final BlockingQueue<DirectoryChangeEvent> processingQueue = new LinkedBlockingQueue<>(100000);

    // 负载检测
    private final AtomicInteger eventBurstCounter = new AtomicInteger(0);
    private final AtomicBoolean highLoadDetected = new AtomicBoolean(false);
    private final AtomicBoolean scanningInProgress = new AtomicBoolean(false);
    private volatile Instant lastBurstTime = Instant.now();
    private static final int EVENT_BURST_THRESHOLD = 5000; // 单位时间内事件数阈值
    private static final Duration BURST_WINDOW = Duration.ofSeconds(10); // 事件计数窗口
    private static final Duration IDLE_THRESHOLD = Duration.ofSeconds(30); // 闲置时间阈值

    private final ScheduledExecutorService scheduler = ThreadUtil.createScheduledExecutor(2);

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final List<Future<?>> consumerFutures = new CopyOnWriteArrayList<>();
    private final ExecutorService processExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private static class EventWrapper {
        final DirectoryChangeEvent event;
        final Instant timestamp;

        EventWrapper(DirectoryChangeEvent event) {
            this.event = event;
            this.timestamp = Instant.now();
        }
    }

    @PostConstruct
    public void init() {
        // 定期将事件从Map转移到处理队列
        scheduler.scheduleAtFixedRate(this::transferEventsToQueue, 200, 200, TimeUnit.MILLISECONDS);

        // 启动消费者线程
        startConsumerThreads();

        // 定期检查是否需要执行全盘扫描
        scheduler.scheduleAtFixedRate(this::checkAndStartScan, 10, 10, TimeUnit.SECONDS);

        // 定期重置事件计数器
        scheduler.scheduleAtFixedRate(() -> {
            eventBurstCounter.set(0);
            // 如果高负载标志已设置且已经过了一段闲置期，则进行一次全盘扫描
            if (highLoadDetected.get() &&
                    Duration.between(lastBurstTime, Instant.now()).compareTo(IDLE_THRESHOLD) > 0) {
                checkAndStartScan();
            }
        }, BURST_WINDOW.toSeconds(), BURST_WINDOW.toSeconds(), TimeUnit.SECONDS);
    }

    private void checkAndStartScan() {
        // 如果检测到高负载并且当前没有扫描正在进行中，且处理队列基本为空（系统闲置）
        if (highLoadDetected.get() && !scanningInProgress.get() && processingQueue.size() < 10) {
            highLoadDetected.set(false); // 重置高负载标志
            startIncrementalScan();
        }
    }

    private void startIncrementalScan() {
        if (!scanningInProgress.compareAndSet(false, true)) {
            return; // 已有扫描任务在运行
        }
        // 延时执行，确保当前处理队列中的事件都被处理完
        rebuildIndexTaskService.doSync(null, null, false);
        rebuildIndexTaskService.onSyncComplete(() -> scanningInProgress.set(false));
    }

    private void transferEventsToQueue() {
        if (eventMap.isEmpty()) {
            return;
        }

        List<Path> keysToRemove = new ArrayList<>();
        List<DirectoryChangeEvent> eventsToProcess = new ArrayList<>();
        Instant now = Instant.now();

        // 遍历 Map，找出已经“稳定”的事件
        for (Map.Entry<Path, EventWrapper> entry : eventMap.entrySet()) {
            if (Duration.between(entry.getValue().timestamp, now).compareTo(GRACE_PERIOD) > 0) {
                // 这个事件的时间戳距离现在已经超过了静默期，说明文件稳定了
                keysToRemove.add(entry.getKey());
                eventsToProcess.add(entry.getValue().event);
            }
        }

        if (keysToRemove.isEmpty()) {
            return;
        }

        // 从 Map 中移除已处理的事件
        for (Path key : keysToRemove) {
            eventMap.remove(key);
        }

        // 转移到处理队列
        int transferCount = 0;
        for (DirectoryChangeEvent event : eventsToProcess) {
            boolean offered = processingQueue.offer(event);
            if (!offered) {
                log.error("处理队列已满，无法添加事件: {}, 路径: {}", event.eventType(), event.path());
                // 放回原Map以便下次尝试
                eventMap.put(event.path(), new EventWrapper(event));
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
            Future<?> f = processExecutor.submit(this::processQueueItems);
            consumerFutures.add(f);
        }
    }

    private void processQueueItems() {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
            try {
                DirectoryChangeEvent event = processingQueue.take();
                try {
                    processEvent(event);
                } catch (Exception e) {
                    log.error("处理事件失败: {}, 路径: {}", event.eventType(), event.path(), e);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
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
        if (!running.get()) {
            return;
        }
        Path eventPath = directoryChangeEvent.path();
        DirectoryChangeEvent.EventType eventType = directoryChangeEvent.eventType();

        // 检查是否为忽略路径
        if (filterDirSet.stream().anyMatch(eventPath::startsWith)) {
            log.debug("忽略事件: {}, 路径: {}", eventType, eventPath);
            return;
        }

        String filename = eventPath.getFileName().toString();
        if (fileProperties.getMonitorIgnoreFilePrefix().stream().anyMatch(filename::startsWith)) {
            log.debug("忽略文件:{}", eventPath.toFile().getAbsolutePath());
            return;
        }

        // 增加事件计数器，检测高负载
        int currentCount = eventBurstCounter.incrementAndGet();
        lastBurstTime = Instant.now();
        if (currentCount > EVENT_BURST_THRESHOLD && !highLoadDetected.get()) {
            highLoadDetected.set(true);
            log.warn("检测到高事件负载: {}事件/{}秒，标记为需要增量扫描", currentCount, BURST_WINDOW.getSeconds());
        }

        log.debug("接收到事件: {}, 路径: {}", eventType, eventPath);

        // 将事件放入Map
        eventMap.put(eventPath, new EventWrapper(directoryChangeEvent));
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
        log.info("Shutting down FileListener...");
        running.set(false);
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        for (Future<?> f : consumerFutures) {
            f.cancel(true);
        }
        processExecutor.shutdown();
        try {
            if (!processExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                processExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            processExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        processingQueue.clear();
        eventMap.clear();
        log.info("FileListener shut down complete.");
    }
}

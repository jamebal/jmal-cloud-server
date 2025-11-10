package com.jmal.clouddisk.listener;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.lucene.RebuildIndexTaskService;
import com.jmal.clouddisk.lucene.ThrottledTaskExecutor;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.util.MyThreadUtil;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileListener implements DirectoryChangeListener {

    private final FileProperties fileProperties;
    private final IFileService fileService;
    private final RebuildIndexTaskService rebuildIndexTaskService;

    // --- 可配置参数 ---
    private static final Duration DEBOUNCE_PERIOD = Duration.ofSeconds(2); // 防抖静默期

    @Getter
    private final Set<Path> filterDirSet = new CopyOnWriteArraySet<>();

    // 用于调度防抖任务
    private final ScheduledExecutorService scheduler = ThreadUtil.createScheduledExecutor(1);
    // 存储每个路径的待处理任务，用于取消和替换
    private final Map<Path, Future<?>> scheduledTasks = new ConcurrentHashMap<>();
    // 使用虚拟线程处理最终的业务逻辑
    private final ThrottledTaskExecutor processExecutor = new ThrottledTaskExecutor(Constants.MAX_CONCURRENT_PROCESSING_NUMBER);

    // --- 高负载与全盘扫描状态 ---
    private static final int IDLE_TASK_THRESHOLD = 5;
    private final AtomicBoolean rescanRequired = new AtomicBoolean(false);
    private final AtomicBoolean scanningInProgress = new AtomicBoolean(false);
    private final ScheduledExecutorService scanScheduler = ThreadUtil.createScheduledExecutor(1);

    private final AtomicBoolean running = new AtomicBoolean(true);

    @PostConstruct
    public void init() {
        // 定期检查是否需要执行全盘扫描
        scanScheduler.scheduleAtFixedRate(this::checkAndTriggerScan, 15, 15, TimeUnit.SECONDS);
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
    public void onEvent(DirectoryChangeEvent event) {
        if (!running.get()) {
            return;
        }

        // 处理 OVERFLOW 事件
        if (event.eventType() == DirectoryChangeEvent.EventType.OVERFLOW) {
            log.warn("文件系统事件队列溢出。可能已丢失事件。安排重新扫描。");
            rescanRequired.set(true);
            // 状态已不可信，取消所有待处理的单个文件任务
            scheduledTasks.values().forEach(future -> future.cancel(false));
            scheduledTasks.clear();
            return;
        }

        Path eventPath = event.path();

        // 过滤忽略的路径和文件
        if (isPathIgnored(eventPath)) {
            return;
        }

        log.debug("收到事件：{}, 路径：{}。安排处理中。", event.eventType(), eventPath);

        // 防抖逻辑
        // 如果该路径已有待处理任务，先取消它
        Future<?> existingTask = scheduledTasks.remove(eventPath);
        if (existingTask != null) {
            existingTask.cancel(false);
        }

        // 安排一个新任务在静默期后执行
        Future<?> newTask = scheduler.schedule(() -> {
            // 任务执行前，从map中移除自己
            scheduledTasks.remove(eventPath);
            // 提交到虚拟线程池执行真正的处理逻辑
            processExecutor.execute(() -> processEventWithThrottling(event));
        }, DEBOUNCE_PERIOD.toMillis(), TimeUnit.MILLISECONDS);

        // 将新任务放入map
        scheduledTasks.put(eventPath, newTask);
    }

    private boolean isPathIgnored(Path eventPath) {
        if (filterDirSet.stream().anyMatch(eventPath::startsWith)) {
            log.debug("忽略过滤目录中的事件：{}", eventPath);
            return true;
        }
        String filename = eventPath.getFileName().toString();
        if (fileProperties.getMonitorIgnoreFilePrefix().stream().anyMatch(filename::startsWith)) {
            log.debug("忽略带有忽略前缀的文件：{}", eventPath);
            return true;
        }
        return false;
    }

    private void processEventWithThrottling(DirectoryChangeEvent event) {
        if (!running.get()) return;
        try {
            processEvent(event);
        } catch (Exception e) {
            log.error("处理路径：{} 的事件时发生未处理的异常", event.path(), e);
        }
    }

    private void processEvent(DirectoryChangeEvent event) {
        // 确保在处理前，服务仍在运行
        if (!running.get()) {
            return;
        }
        try {
            log.debug("处理事件：{}, 路径：{}", event.eventType(), event.path());
            File file = event.path().toFile();
            String username = ownerOfChangeFile(file);
            if (CharSequenceUtil.isBlank(username)) {
                return;
            }

            switch (event.eventType()) {
                case CREATE -> {
                    log.info("用户：{}, 创建文件： {}", username, file.getAbsolutePath());
                    fileService.createFile(username, file);
                }
                case MODIFY -> {
                    log.info("用户：{}, 修改文件：{}", username, file.getAbsolutePath());
                    fileService.updateFile(username, file);
                }
                case DELETE -> {
                    log.info("用户：{}, 删除文件：{}", username, file.getAbsolutePath());
                    fileService.deleteFile(username, file);
                }
                default -> {
                }
            }
        } catch (Exception e) {
            log.error("处理路径：{}的事件失败", event.path(), e);
        }
    }

    private void checkAndTriggerScan() {
        // 如果需要重新扫描，且当前没有扫描任务在进行，并且待处理的单个事件很少（系统趋于空闲）
        if (rescanRequired.get() && scheduledTasks.size() < IDLE_TASK_THRESHOLD && scanningInProgress.compareAndSet(false, true)) {
            log.info("系统空闲，需要重新扫描。开始增量扫描...");
            rescanRequired.set(false); // 重置标志
            try {
                // 异步执行，并在完成后重置扫描状态
                rebuildIndexTaskService.doSync(null, null, false);
                rebuildIndexTaskService.onSyncComplete(() -> {
                    log.info("增量扫描完成成功。");
                    scanningInProgress.set(false);
                });
            } catch (Exception e) {
                log.error("增量扫描启动失败。", e);
                scanningInProgress.set(false);
            }
        }
    }

    private String ownerOfChangeFile(File file) {
        try {
            int rootPathCount = Paths.get(fileProperties.getRootDir()).getNameCount();
            int filePathCount = file.toPath().getNameCount();
            if (filePathCount <= rootPathCount) {
                return null;
            }
            if (filePathCount == rootPathCount + 1) {
                // 根目录下的文件，忽略
                return null;
            }
            return file.toPath().subpath(rootPathCount, rootPathCount + 1).toString();
        } catch (Exception e) {
            log.error("解析路径中的所有者失败：{}", file.getAbsolutePath(), e);
            return null;
        }
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

        processExecutor.shutdown();

        MyThreadUtil.shutdownExecutor(scheduler, "Scheduler");
        MyThreadUtil.shutdownExecutor(scanScheduler, "ScanScheduler");

        scheduledTasks.clear();
        log.info("FileListener shut down complete.");
    }
}

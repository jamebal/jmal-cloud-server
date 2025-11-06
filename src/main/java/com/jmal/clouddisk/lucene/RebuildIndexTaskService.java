package com.jmal.clouddisk.lucene;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.model.file.dto.FileBaseDTO;
import com.jmal.clouddisk.ocr.OcrService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.CommonUserFileService;
import com.jmal.clouddisk.service.impl.CommonUserService;
import com.jmal.clouddisk.service.impl.MenuService;
import com.jmal.clouddisk.service.impl.MessageService;
import com.jmal.clouddisk.service.impl.PathService;
import com.jmal.clouddisk.service.impl.RoleService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.ThrottleExecutor;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.IndexWriter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.RoundingMode;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>重建索引任务</p>
 * 重建索引分为两个步骤<br>
 * 1. 同步文件到数据库<br>
 * 2. 创建文件内容索引<br>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RebuildIndexTaskService implements ApplicationListener<RebuildIndexEvent> {

    private final PathService pathService;

    private final IndexWriter indexWriter;

    private final MenuService menuService;

    private final RoleService roleService;

    private final CommonUserService commonUserService;

    private final FileProperties fileProperties;

    private final CommonFileService commonFileService;

    private final CommonUserFileService commonUserFileService;

    private final MessageService messageService;

    private final IFileDAO fileDAO;

    private final ThrottledTaskExecutor syncFileVisitorService = new ThrottledTaskExecutor(Constants.MAX_CONCURRENT_PROCESSING_NUMBER);

    private SyncFileVisitor syncFileVisitor;

    private double totalCount;

    private final OcrService ocrService;

    private final LuceneReconciliationService luceneReconciliationService;

    /**
     * 接收消息的用户
     */
    private static final Set<String> RECIPIENT = new CopyOnWriteArraySet<>();

    private static final String MSG_SYNCED = "synced";

    /**
     * 待索引任务总数
     */
    private static final AtomicInteger NOT_INDEX_TASK_SIZE = new AtomicInteger(0);
    /**
     * 完成索引任务数
     */
    private static final AtomicInteger INDEXED_TASK_SIZE = new AtomicInteger(0);

    private static final long SCAN_TERMINATION_TIMEOUT_MINUTES = 60;

    /**
     * 同步文件操作锁, 防止重复操作
     */
    private static final ReentrantLock SYNC_FILE_LOCK = new ReentrantLock();

    private Runnable syncCompleteCallback;

    /**
     * 进度百分比
     */
    private static final Map<String, Double> PERCENT_MAP = new ConcurrentHashMap<>(2);
    /**
     * 同步进度
     */
    private static final String SYNC_PERCENT = "syncPercent";
    /**
     * 索引进度
     */
    private static final String INDEXING_PERCENT = "indexingPercent";

    private ThrottleExecutor throttleExecutor;

    private Timer DELAY_DELETE_TAG_TIMER;

    private final ReentrantLock deleteDocWithDeleteFlagLock = new ReentrantLock();

    @EventListener(ContextRefreshedEvent.class)
    public void onApplicationReady(ContextRefreshedEvent event) {
        if (event.getApplicationContext().getParent() != null) {
            return;
        }
        ThreadUtil.execute(this::init);
    }

    public void init() {
        // 启动时检测是否存在菜单，不存在则初始化
        if (!menuService.existsMenu()) {
            menuService.initMenus();
            roleService.initRoles();
        }
        // 启动时检测是否存在lucene索引，不存在则初始化
        if (!checkIndexExists()) {
            doSync(commonUserService.getCreatorUsername(), null, true);
        }
        // 重置索引状态
        resetIndexStatus();
    }


    @Override
    public void onApplicationEvent(RebuildIndexEvent event) {
        if (event.getUsername() != null && event.getFileAbsolutePath() != null) {
            doSync(event.getUsername(), event.getFileAbsolutePath(), false);
        }
    }

    public void doSync(String username, String path, boolean isDelIndex) {
        if (isSyncFile() || isIndexing()) {
            return;
        }
        if (StrUtil.isBlank(username)) {
            username = commonUserService.getCreatorUsername();
        }
        setPercentMap(0d, 0d);
        String finalUsername = username;
        Completable.fromAction(() -> {
            if (!SYNC_FILE_LOCK.tryLock()) {
                return;
            }
            try {
                Path canPath;
                if (StrUtil.isBlank(path)) {
                    canPath = Paths.get(fileProperties.getRootDir());
                } else {
                    canPath = Paths.get(path);
                }
                if (!Files.exists(canPath)) {
                    return;
                }
                rebuildingIndex(finalUsername, canPath, isDelIndex);
            } finally {
                SYNC_FILE_LOCK.unlock();
                setPercentMap(100d, 100d);
                pushMessage();
            }
        }).subscribeOn(Schedulers.io())
                .doOnError(e -> log.error(e.getMessage(), e))
                .onErrorComplete()
                .subscribe();
    }

    /**
     * 重建索引
     *
     * @param recipient 接收消息的用户
     * @param path      要扫描的路径
     * @param isDelIndex 是否删除索引
     */
    private void rebuildingIndex(String recipient, Path path, boolean isDelIndex) {
        TimeInterval timeInterval = new TimeInterval();
        try {
            getRecipient(recipient);
            restIndexedTasks();
            if (DELAY_DELETE_TAG_TIMER != null) {
                DELAY_DELETE_TAG_TIMER.cancel();
            }
            Set<FileVisitOption> fileVisitOptions = EnumSet.noneOf(FileVisitOption.class);
            fileVisitOptions.add(FileVisitOption.FOLLOW_LINKS);
            // 先移除删除标记, 以免因为扫描路径的不同导致删除标记未移除
            removeDeletedFlag(null);
            // 添加删除标记, 扫描完后如果标记还在则删除
            addDeleteFlagOfDoc(path, isDelIndex);
            // 重置索引状态
            resetIndexStatus();
            FileCountVisitor fileCountVisitor = new FileCountVisitor();
            Files.walkFileTree(path, fileVisitOptions, Integer.MAX_VALUE, fileCountVisitor);
            totalCount = fileCountVisitor.getCount();
            log.info("path: {}, 开始扫描, 文件数: {}", path, totalCount);
            timeInterval.start();
            if (syncFileVisitor == null) {
                syncFileVisitor = new SyncFileVisitor(totalCount);
            }
            Files.walkFileTree(path, fileVisitOptions, Integer.MAX_VALUE, syncFileVisitor);
            // 等待线程池里所有任务完成
            waitTaskCompleted();
            deleteDocWithDeleteFlag();
        } catch (IOException e) {
            log.error("{}{}", e.getMessage(), path, e);
        } finally {
            setPercentMap(100d, getIndexedPercentValue());
            syncFileVisitor = null;
            log.info("扫描完成, 耗时: {}s", Convert.toDouble(timeInterval.intervalMs() / 1000));
        }
    }

    private void waitTaskCompleted() {
        try {
            log.info("等待扫描文件完成");
            // 等待线程池里所有任务完成
            if (!syncFileVisitorService.awaitTermination(SCAN_TERMINATION_TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                log.warn("扫描文件超时, 尝试强制停止所有任务");
                // 移除删除标记, 以免误删索引
                removeDeletedFlag(null);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(e.getMessage(), e);
        }
    }

    private String getRecipient(String username) {
        if (StrUtil.isNotBlank(username)) {
            RECIPIENT.clear();
            RECIPIENT.add(username);
        }
        return RECIPIENT.stream().findFirst().orElse(username);
    }

    private void deleteDocWithDeleteFlag() {
        DELAY_DELETE_TAG_TIMER = new Timer();
        DELAY_DELETE_TAG_TIMER.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!deleteDocWithDeleteFlagLock.tryLock()) {
                    return;
                }
                try {
                    commonFileService.deleteDocWithDeleteFlag();
                } finally {
                    deleteDocWithDeleteFlagLock.unlock();
                }
            }
        }, 10000);
    }

    public void onSyncComplete(Runnable callback) {
        this.syncCompleteCallback = callback;
    }

    public void rebuildingIndexCompleted() {
        if (!hasUnIndexedTasks() && NOT_INDEX_TASK_SIZE.get() > 0) {
            setPercentMap(100d, 100d);
            log.debug("重建索引完成, INDEXED_TASK_SIZE, {}, NOT_INDEX_TASK_SIZE: {}", INDEXED_TASK_SIZE, NOT_INDEX_TASK_SIZE);
            restIndexedTasks();
            pushMessage();
            if (syncCompleteCallback != null) {
                syncCompleteCallback.run();
            }
        }
    }

    private void restIndexedTasks() {
        NOT_INDEX_TASK_SIZE.set(0);
        INDEXED_TASK_SIZE.set(0);
        totalCount = 0;
    }

    /**
     * 增加待索引任务数
     */
    public void incrementNotIndexTaskSize() {
        NOT_INDEX_TASK_SIZE.incrementAndGet();
    }

    /**
     * 增加已索引任务数
     */
    public void incrementIndexedTaskSize() {
        INDEXED_TASK_SIZE.incrementAndGet();
        delayResetIndex();
    }

    public void delayResetIndex() {
        synchronized (RebuildIndexTaskService.class) {
            if (throttleExecutor == null) {
                throttleExecutor = new ThrottleExecutor(10000);
            }
        }
        throttleExecutor.cancel();
        throttleExecutor.schedule(this::rebuildingIndexCompleted);
    }

    private void updatePercent() {
        setPercentMap(null, null);
        pushMessage();
    }

    /**
     * 是否正在同步文件
     */
    public static boolean isSyncFile() {
        return SYNC_FILE_LOCK.isLocked();
    }

    /**
     * 是否正在创建索引
     */
    public static boolean isIndexing() {
        return NOT_INDEX_TASK_SIZE.get() > 0;
    }

    /**
     * 把文件同步到数据库
     * @param username 用户名
     * @param path 文件相对路径
     * @param isDelIndex 是否删除索引
     */
    public ResponseResult<Object> sync(String username, String path, boolean isDelIndex) {
        if (StrUtil.isNotBlank(path)) {
            path = Paths.get(fileProperties.getRootDir(), username, path).toString();
        }
        doSync(username, path, isDelIndex);
        return ResultUtil.success();
    }

    /**
     * 是否正在同步中
     */
    public Map<String, Double> isSync() {
        if (PERCENT_MAP.isEmpty()) {
            setPercentMap(100d, 100d);
        }
        return PERCENT_MAP;
    }

    /**
     * 更新任务进度, 用于前端显示
     */
    public void updateTaskProgress() {
        if (!isSyncFile() && !isIndexing()) {
            return;
        }
        if (getRecipient(null) == null) {
            getRecipient(commonUserService.getCreatorUsername());
        }
        // 更新进度
        updatePercent();
    }

    /**
     * 获取同步进度
     */
    private double getSyncPercent() {
        if (syncFileVisitor == null) {
            return PERCENT_MAP.getOrDefault(SYNC_PERCENT, 100d);
        }
        double percent = syncFileVisitor.getPercent();
        if (percent > 100) {
            return 100;
        }
        return percent;
    }

    /**
     * 获取索引进度
     */
    private double getIndexedPercent() {
        if (NOT_INDEX_TASK_SIZE.get() == 0 || isSyncFile()) {
            return 100;
        }
        double percent = getIndexedPercentValue();
        if (percent > 100) {
            return 100;
        }
        return percent;
    }

    private double getIndexedPercentValue() {
        if (totalCount == 0) {
            return 0;
        }
        return NumberUtil.round((double) INDEXED_TASK_SIZE.get() / totalCount * 100, 2, RoundingMode.DOWN).doubleValue();
    }

    private class SyncFileVisitor extends SimpleFileVisitor<Path> {

        @Getter
        public final double totalCount;

        private final AtomicInteger processCount;

        public SyncFileVisitor(double totalCount) {
            this.totalCount = totalCount;
            this.processCount = new AtomicInteger(0);
        }

        public double getPercent() {
            return NumberUtil.round(processCount.get() / totalCount * 100, 2).doubleValue();
        }

        @NotNull
        @Override
        public FileVisitResult visitFileFailed(@NotNull Path file, @NotNull IOException exc) throws IOException {
            log.error(exc.getMessage(), exc);
            return super.visitFileFailed(file, exc);
        }

        @NotNull
        @Override
        public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
            // 跳过临时文件目录
            FileVisitResult skipTempDirectory = skipTempDirectory(dir);
            if (skipTempDirectory != null) return skipTempDirectory;
            String username = pathService.getUsernameByAbsolutePath(dir);
            processCount.incrementAndGet();
            if (StrUtil.isBlank(username)) {
                return super.visitFile(dir, attrs);
            }
            processFile(dir, username);
            return super.preVisitDirectory(dir, attrs);
        }

        @NotNull
        @Override
        public FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attrs) throws IOException {
            // 判断文件名是否在monitorIgnoreFilePrefix中
            String filename = file.getFileName().toString();
            if (fileProperties.getMonitorIgnoreFilePrefix().stream().anyMatch(filename::startsWith)) {
                log.debug("忽略文件:{}", file.getFileName());
                return super.visitFile(file, attrs);
            }
            processCount.incrementAndGet();
            String username = pathService.getUsernameByAbsolutePath(file);
            if (StrUtil.isBlank(username)) {
                return super.visitFile(file, attrs);
            }
            processFile(file, username);
            return super.visitFile(file, attrs);
        }

        private void processFile(Path file, String username) {
            syncFileVisitorService.execute(() -> createFile(username, file));
        }

        private void createFile(String username, Path file) {
            try {
                commonUserFileService.createFile(username, file.toFile(), null, null);
            } catch (Exception e) {
                log.error("createFile error {}{}", e.getMessage(), file, e);
                FileBaseDTO fileBaseDTO = commonFileService.getFileBaseDTO(username, file.toFile().getAbsolutePath());
                String fileId = fileDAO.findIdByUserIdAndPathAndName(fileBaseDTO.getUserId(), fileBaseDTO.getName(), fileBaseDTO.getPath());
                if (fileId != null) {
                    // 需要移除删除标记
                    removeDeletedFlag(Collections.singletonList(fileId));
                }
            }
        }
    }

    private void setPercentMap(Double syncPercent, Double indexingPercent) {
        if (syncPercent == null) {
            syncPercent = getSyncPercent();
        }
        if (indexingPercent == null) {
            indexingPercent = getIndexedPercent();
        }
        PERCENT_MAP.put(SYNC_PERCENT, syncPercent);
        PERCENT_MAP.put(INDEXING_PERCENT, indexingPercent);
        if (syncPercent >= 100 && indexingPercent >= 100) {
            ocrService.setMaxConcurrentRequests(ocrService.getOcrConfig().getMaxTasks());
        }
    }

    private void pushMessage() {
        messageService.pushMessage(getRecipient(null), PERCENT_MAP, RebuildIndexTaskService.MSG_SYNCED);
        log.debug("索引进度: {}, isSyncFile: {}, INDEXED_TASK_SIZE, {}, NOT_INDEX_TASK_SIZE: {}", PERCENT_MAP, isSyncFile(), INDEXED_TASK_SIZE.get(), NOT_INDEX_TASK_SIZE.get());
    }

    private class FileCountVisitor extends SimpleFileVisitor<Path> {
        private final AtomicLong count = new AtomicLong(0);

        public long getCount() {
            return count.get();
        }

        @NotNull
        @Override
        public FileVisitResult preVisitDirectory(@NotNull Path dir, @NotNull BasicFileAttributes attrs) throws IOException {
            // 跳过临时文件目录
            FileVisitResult skipTempDirectory = skipTempDirectory(dir);
            if (skipTempDirectory != null) return skipTempDirectory;
            String username = pathService.getUsernameByAbsolutePath(dir);
            count.addAndGet(1);
            if (StrUtil.isBlank(username)) {
                return super.visitFile(dir, attrs);
            }
            return super.preVisitDirectory(dir, attrs);
        }

        @NotNull
        @Override
        public FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) throws IOException {
            count.addAndGet(1);
            return super.visitFile(file, attrs);
        }
    }

    private @Nullable FileVisitResult skipTempDirectory(Path dir) {
        // 跳过临时文件目录
        if (dir.startsWith(Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir()))) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        // 跳过lucene索引目录
        if (dir.startsWith(Paths.get(fileProperties.getRootDir(), fileProperties.getLuceneIndexDir()))) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        // 跳过 jmalcloud 目录
        if (dir.startsWith(Paths.get(fileProperties.getRootDir(), fileProperties.getJmalcloudDBDir()))) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        return null;
    }

    public boolean checkIndexExists() {
        long count = fileDAO.countOssFolder();
        long indexCount = indexWriter.getDocStats().numDocs;
        return indexCount > count;
    }

    /**
     * 重置索引状态, 将正在索引的文件状态重置为未索引
     */
    public void resetIndexStatus() {
        fileDAO.resetIndexStatus();
    }

    /**
     * 检查是否有未索引或正在索引的任务
     */
    public boolean hasUnIndexedTasks() {
        return fileDAO.existsByUnIndexed();
    }

    /**
     * 添加删除标记
     */
    private void addDeleteFlagOfDoc(Path filepath, boolean isDelIndex) {
        if (filepath == null) {
            return;
        }
        if (Files.notExists(filepath)) {
            return;
        }
        if (filepath.toFile().isFile()) {
            return;
        }
        String username = pathService.getUsernameByAbsolutePath(filepath);
        String userId = null;
        String path = null;
        if (StrUtil.isNotBlank(username)) {
            userId = commonUserService.getUserIdByUserName(username);
            Path relativePath = Paths.get(fileProperties.getRootDir(), username).relativize(filepath);
            if (relativePath.getNameCount() > 0) {
                path = "/" + relativePath + "/";
            } else {
                path = "/";
            }
        }
        fileDAO.setDelTag(userId, path);
        if (isDelIndex) {
            // 开启索引对账, 删除孤儿索引
            luceneReconciliationService.startReconciliation();
        }
    }

    /**
     * 移除删除标记
     *
     * @param fileIdList fileIdList
     */
    public void removeDeletedFlag(List<String> fileIdList) {
        fileDAO.UnsetDelTagByIdIn(fileIdList);
    }

    @PreDestroy
    public void destroy() {
        syncFileVisitorService.shutdown();
        if (throttleExecutor != null) {
            throttleExecutor.shutdown();
        }
    }

}

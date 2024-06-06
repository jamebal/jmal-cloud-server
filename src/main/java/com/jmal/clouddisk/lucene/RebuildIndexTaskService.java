package com.jmal.clouddisk.lucene;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.ReUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.MenuService;
import com.jmal.clouddisk.service.impl.RoleService;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.ThrottleExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.IndexWriter;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.RoundingMode;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
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
public class RebuildIndexTaskService {

    private final IndexWriter indexWriter;

    private final MenuService menuService;

    private final RoleService roleService;

    private final UserServiceImpl userService;

    private final FileProperties fileProperties;

    private final CommonFileService commonFileService;

    private final MongoTemplate mongoTemplate;

    private ExecutorService syncFileVisitorService;

    private SyncFileVisitor syncFileVisitor;

    private double totalCount;

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

    /**
     * 同步文件操作锁, 防止重复操作
     */
    private static final ReentrantLock SYNC_FILE_LOCK = new ReentrantLock();

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

    @PostConstruct
    public void init() {
        // 启动时检测是否存在菜单，不存在则初始化
        if (!menuService.existsMenu()) {
            log.info("初始化角色、菜单！");
            menuService.initMenus();
            roleService.initRoles();
        }
        // 启动时检测是否存在lucene索引，不存在则初始化
        if (!checkIndexExists()) {
            doSync(userService.getCreatorUsername(), null);
        }
        // 重置索引状态
        resetIndexStatus();
    }

    private void getSyncFileVisitorService() {
        int processors = Runtime.getRuntime().availableProcessors() - 1;
        if (processors < 1) {
            processors = 1;
        }
        if (syncFileVisitorService == null || syncFileVisitorService.isShutdown()) {
            syncFileVisitorService = ThreadUtil.newFixedExecutor(processors, 1, "syncFileVisitor", true);
        }
    }

    public void doSync(String username, String path) {
        if (isSyncFile() || isIndexing()) {
            return;
        }
        setPercentMap(0d, 0d);
        ThreadUtil.execute(() -> {
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
                getSyncFileVisitorService();
                rebuildingIndex(username, canPath);
            } finally {
                SYNC_FILE_LOCK.unlock();
            }
        });
    }

    /**
     * 重建索引
     *
     * @param recipient 接收消息的用户
     * @param path      要扫描的路径
     */
    private void rebuildingIndex(String recipient, Path path) {
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
            addDeleteFlagOfDoc(path);
            // 重置索引状态
            resetIndexStatus();
            FileCountVisitor fileCountVisitor = new FileCountVisitor();
            Files.walkFileTree(path, fileVisitOptions, Integer.MAX_VALUE, fileCountVisitor);
            totalCount = fileCountVisitor.getCount();
            log.info("path: {}, 开始同步, 文件数: {}", path, totalCount);
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
            log.info("同步完成, 耗时: {}s", timeInterval.intervalSecond());
        }
    }

    private void waitTaskCompleted() {
        try {
            log.info("等待同步文件完成");
            syncFileVisitorService.shutdown();
            // 等待线程池里所有任务完成
            if (!syncFileVisitorService.awaitTermination(10, TimeUnit.MINUTES)) {
                log.warn("同步文件超时, 尝试强制停止所有任务");
                syncFileVisitorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            syncFileVisitorService.shutdownNow();
            Thread.currentThread().interrupt();
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
                commonFileService.deleteDocWithDeleteFlag();
            }
        }, 60000 * 3);
    }

    public void rebuildingIndexCompleted() {
        if (!hasUnIndexedTasks() && NOT_INDEX_TASK_SIZE.get() > 0) {
            setPercentMap(100d, 100d);
            log.debug("重建索引完成, INDEXED_TASK_SIZE, {}, NOT_INDEX_TASK_SIZE: {}", INDEXED_TASK_SIZE, NOT_INDEX_TASK_SIZE);
            restIndexedTasks();
            pushMessage();
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

    private void delayResetIndex() {
        synchronized (RebuildIndexTaskService.class) {
            if (throttleExecutor == null) {
                throttleExecutor = new ThrottleExecutor(3000);
            }
        }
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
     */
    public ResponseResult<Object> sync(String username, String path) {
        if (StrUtil.isNotBlank(path)) {
            path = Paths.get(fileProperties.getRootDir(), username, path).toString();
        }
        doSync(username, path);
        return ResultUtil.success();
    }

    /**
     * 是否正在同步中
     */
    public Map<String, Double> isSync() {
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
            getRecipient(userService.getCreatorUsername());
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
        return syncFileVisitor.getPercent();
    }

    /**
     * 获取索引进度
     */
    private double getIndexedPercent() {
        if (NOT_INDEX_TASK_SIZE.get() == 0 || isSyncFile()) {
            return 100;
        }
        return getIndexedPercentValue();
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

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            log.error(exc.getMessage(), exc);
            return super.visitFileFailed(file, exc);
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            // 跳过临时文件目录
            FileVisitResult skipTempDirectory = skipTempDirectory(dir);
            if (skipTempDirectory != null) return skipTempDirectory;
            String username = commonFileService.getUsernameByAbsolutePath(dir);
            processCount.incrementAndGet();
            if (StrUtil.isBlank(username)) {
                return super.visitFile(dir, attrs);
            }
            syncFileVisitorService.execute(() -> createFile(username, dir));
            return super.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            processCount.incrementAndGet();
            String username = commonFileService.getUsernameByAbsolutePath(file);
            if (StrUtil.isBlank(username)) {
                return super.visitFile(file, attrs);
            }
            syncFileVisitorService.execute(() -> createFile(username, file));
            return super.visitFile(file, attrs);
        }

        private void createFile(String username, Path file) {
            try {
                commonFileService.createFile(username, file.toFile(), null, null);
            } catch (Exception e) {
                log.error("{}{}", e.getMessage(), file, e);
                FileDocument fileDocument = commonFileService.getFileDocument(username, file.toFile().getAbsolutePath());
                if (fileDocument != null) {
                    // 需要移除删除标记
                    removeDeletedFlag(Collections.singletonList(fileDocument.getId()));
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
    }

    private void pushMessage() {
        commonFileService.pushMessage(getRecipient(null), PERCENT_MAP, RebuildIndexTaskService.MSG_SYNCED);
        log.debug("推送消息: {}, isSyncFile: {}, INDEXED_TASK_SIZE, {}, NOT_INDEX_TASK_SIZE: {}", PERCENT_MAP, isSyncFile(), INDEXED_TASK_SIZE.get(), NOT_INDEX_TASK_SIZE.get());
    }

    private class FileCountVisitor extends SimpleFileVisitor<Path> {
        private final AtomicLong count = new AtomicLong(0);

        public long getCount() {
            return count.get();
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            // 跳过临时文件目录
            FileVisitResult skipTempDirectory = skipTempDirectory(dir);
            if (skipTempDirectory != null) return skipTempDirectory;
            String username = commonFileService.getUsernameByAbsolutePath(dir);
            count.addAndGet(1);
            if (StrUtil.isBlank(username)) {
                return super.visitFile(dir, attrs);
            }
            return super.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            count.addAndGet(1);
            return super.visitFile(file, attrs);
        }
    }

    private @Nullable FileVisitResult skipTempDirectory(Path dir) {
        // 跳过临时文件目录
        if (dir.toFile().getName().equals(fileProperties.getChunkFileDir())) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        // 跳过lucene索引目录
        if (dir.toFile().getName().equals(fileProperties.getLuceneIndexDir())) {
            return FileVisitResult.SKIP_SUBTREE;
        }
        return null;
    }

    public boolean checkIndexExists() {
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where("ossFolder").exists(true));
        long count = mongoTemplate.count(query, CommonFileService.COLLECTION_NAME);
        long indexCount = indexWriter.getDocStats().numDocs;
        return indexCount > count;
    }

    /**
     * 重置索引状态, 将正在索引的文件状态重置为未索引
     */
    public void resetIndexStatus() {
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where(LuceneService.MONGO_INDEX_FIELD).lte(IndexStatus.INDEXING.getStatus()));
        Update update = new Update();
        update.unset(LuceneService.MONGO_INDEX_FIELD);
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
    }

    /**
     * 检查是否有未索引或正在索引的任务
     */
    public boolean hasUnIndexedTasks() {
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where(LuceneService.MONGO_INDEX_FIELD).lte(IndexStatus.INDEXING.getStatus()));
        long count = mongoTemplate.count(query, CommonFileService.COLLECTION_NAME);
        return count > 0;
    }

    /**
     * 添加删除标记
     */
    private void addDeleteFlagOfDoc(Path filepath) {
        if (filepath == null) {
            return;
        }
        if (Files.notExists(filepath)) {
            return;
        }
        if (filepath.toFile().isFile()) {
            return;
        }
        String username = commonFileService.getUsernameByAbsolutePath(filepath);
        String userId = null;
        String path = null;
        if (StrUtil.isNotBlank(username)) {
            userId = userService.getUserIdByUserName(username);
            Path relativePath = Paths.get(fileProperties.getRootDir(), username).relativize(filepath);
            if (relativePath.getNameCount() > 0) {
                path = "/" + relativePath + "/";
            } else {
                path = "/";
            }
        }
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where("alonePage").exists(false));
        query.addCriteria(Criteria.where("release").exists(false));
        if (StrUtil.isNotBlank(userId)) {
            query.addCriteria(Criteria.where("userId").is(userId));
        }
        if (StrUtil.isNotBlank(path)) {
            query.addCriteria(Criteria.where("path").regex("^" + ReUtil.escape(path)));
        }
        Update update = new Update();
        // 添加删除标记用于在之后删除
        update.set("delete", 1);
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
    }

    /**
     * 移除删除标记
     *
     * @param fileIdList fileIdList
     */
    public void removeDeletedFlag(List<String> fileIdList) {
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        if (fileIdList == null || fileIdList.isEmpty()) {
            query.addCriteria(Criteria.where("delete").is(1));
        } else {
            query.addCriteria(Criteria.where("_id").in(fileIdList).and("delete").is(1));
        }
        Update update = new Update();
        update.unset("delete");
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
    }

    @PreDestroy
    public void destroy() {
        if (syncFileVisitorService != null) {
            syncFileVisitorService.shutdown();
        }
        if (throttleExecutor != null) {
            throttleExecutor.shutdown();
        }
    }

}

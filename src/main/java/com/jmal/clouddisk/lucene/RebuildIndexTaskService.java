package com.jmal.clouddisk.lucene;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.thread.ThreadUtil;
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
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.index.IndexWriter;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
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

    /**
     * 接收消息的用户
     */
    private static final Set<String> RECIPIENT = new CopyOnWriteArraySet<>();

    /**
     * 已完成的索引进度
     */
    private int indexingPercent;

    /**
     * 已完成的同步进度
     */
    private int syncPercent;

    public static final String MSG_INDEXING = "indexing";

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
     * 是否正在重建索引, 用于判断是重建索引,还是普通的文件添加/修改/删除操作<br>
     * 只有用户点击重建索引时,且文件同步完成后才会为true直到重建索引完成
     */
    private static final AtomicBoolean REBUILDING_INDEX = new AtomicBoolean(false);

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
    }

    private void getSyncFileVisitorService() {
        int processors = Runtime.getRuntime().availableProcessors() - 2;
        if (processors < 1) {
            processors = 1;
        }
        if (syncFileVisitorService == null || syncFileVisitorService.isShutdown()) {
            syncFileVisitorService = ThreadUtil.newFixedExecutor(processors, 1, "syncFileVisitor", true);
        }
    }

    public void doSync(String username, String path) {
        if (!isNotRebuildingIndex()) {
            return;
        }
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
            rebuildingIndexCompleted(recipient);
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
            log.info("path: {}, 开始同步, 文件数: {}", path, fileCountVisitor.getCount());
            timeInterval.start();
            if (syncFileVisitor == null) {
                syncFileVisitor = new SyncFileVisitor(fileCountVisitor.getCount());
            }
            // 开启线程池
            getSyncFileVisitorService();
            Files.walkFileTree(path, fileVisitOptions, Integer.MAX_VALUE, syncFileVisitor);
            // 等待线程池里所有任务完成
            waitTaskCompleted();
            // 同步文件完成
            REBUILDING_INDEX.set(true);
            deleteDocWithDeleteFlag();
        } catch (IOException e) {
            log.error("{}{}", e.getMessage(), path, e);
        } finally {
            syncFileVisitor = null;
            log.info("同步完成, 耗时: {}s", timeInterval.intervalSecond());
            pushMessage(getRecipient(null), 100, MSG_SYNCED);
            pushMessage(getRecipient(null), 0, RebuildIndexTaskService.MSG_INDEXING);
        }
    }

    private void waitTaskCompleted() {
        try {
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

    public void rebuildingIndexCompleted(String recipient) {
        log.info("重建索引完成, INDEXED_TASK_SIZE, {}, NOT_INDEX_TASK_SIZE: {}", INDEXED_TASK_SIZE, NOT_INDEX_TASK_SIZE);
        REBUILDING_INDEX.set(false);
        NOT_INDEX_TASK_SIZE.set(0);
        INDEXED_TASK_SIZE.set(0);
        indexingPercent = 0;
        pushMessage(getRecipient(recipient), 100, RebuildIndexTaskService.MSG_INDEXING);
    }

    public void incrementNotIndexTaskSize(int size) {
        NOT_INDEX_TASK_SIZE.addAndGet(size);
    }

    public void incrementIndexedTaskSize() {
        INDEXED_TASK_SIZE.incrementAndGet();
        if (isNotSyncFile() && !isNotRebuildingIndex()) {
            double percent = (double) INDEXED_TASK_SIZE.get() / NOT_INDEX_TASK_SIZE.get();
            int currentPercent = (int) (percent * 100);
            if (currentPercent > indexingPercent) {
                pushMessage(getRecipient(null), indexingPercent, RebuildIndexTaskService.MSG_INDEXING);
            }
            indexingPercent = currentPercent;
        }
    }

    /**
     * 是否没在重建索引
     */
    public static boolean isNotRebuildingIndex() {
        return isNotSyncFile() && !REBUILDING_INDEX.get();
    }

    /**
     * 是否没在同步文件
     */
    public static boolean isNotSyncFile() {
        return !SYNC_FILE_LOCK.isLocked();
    }

    /***
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
    public Map<String, Object> isSync() {
        Map<String, Object> percentMap = new HashMap<>(2);
        percentMap.put("syncPercent", 100);
        percentMap.put("indexingPercent", 100);
        if (isNotRebuildingIndex()) {
            return percentMap;
        }
        int syncPercent = 100;
        if (syncFileVisitor != null && this.syncPercent < 100) {
            syncPercent = this.syncPercent;
        }
        percentMap.put("syncPercent", syncPercent);

        int indexingPercent = this.indexingPercent;
        percentMap.put("indexingPercent", indexingPercent);
        return percentMap;
    }

    private class SyncFileVisitor extends SimpleFileVisitor<Path> {

        private final double totalCount;

        private final AtomicInteger processCount;

        public SyncFileVisitor(double totalCount) {
            this.totalCount = totalCount;
            this.processCount = new AtomicInteger(0);
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            log.error(exc.getMessage(), exc);
            return super.visitFileFailed(file, exc);
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            // 跳过临时文件目录
            if (dir.toFile().getName().equals(fileProperties.getChunkFileDir())) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            // 跳过lucene索引目录
            if (dir.toFile().getName().equals(fileProperties.getLuceneIndexDir())) {
                return FileVisitResult.SKIP_SUBTREE;
            }
            String username = commonFileService.getUsernameByAbsolutePath(dir);
            if (StrUtil.isBlank(username)) {
                return super.visitFile(dir, attrs);
            }
            syncFileVisitorService.execute(() -> createFile(username, dir));
            return super.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
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
            } finally {
                processCount.incrementAndGet();
                double percent = processCount.get() / totalCount;
                int currentPercent = (int) (percent * 100);
                if (currentPercent > syncPercent) {
                    pushMessage(getRecipient(null), currentPercent, MSG_SYNCED);
                }
                syncPercent = currentPercent;
            }
        }
    }

    public void pushMessage(String recipient, int percent, String message) {
        commonFileService.pushMessage(recipient, percent, message);
        log.info("recipient: {}, percent: {}, message: {}", recipient, percent, message);
    }

    private static class FileCountVisitor extends SimpleFileVisitor<Path> {
        private final AtomicLong count = new AtomicLong(0);

        public long getCount() {
            return count.get();
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            count.addAndGet(1);
            return super.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            count.addAndGet(1);
            return super.visitFile(file, attrs);
        }
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
        query.addCriteria(Criteria.where(LuceneService.MONGO_INDEX_FIELD).is(IndexStatus.INDEXING.getStatus()));
        Update update = new Update();
        update.set(LuceneService.MONGO_INDEX_FIELD, IndexStatus.NOT_INDEX.getStatus());
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
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
    }

}

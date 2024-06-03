package com.jmal.clouddisk.lucene;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.thread.ThreadUtil;
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
import lombok.Getter;
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
import java.util.concurrent.ExecutorService;
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

    private ExecutorService executorService;

    private SyncFileVisitor syncFileVisitor;

    /**
     * 接收消息的用户
     */
    private String recipient;

    /**
     * 已完成的索引进度
     */
    private int indexingPercent;

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
            doSync(userService.getCreatorUsername());
        }
        int processors = Runtime.getRuntime().availableProcessors() - 2;
        if (processors < 1) {
            processors = 1;
        }
        executorService = ThreadUtil.newFixedExecutor(processors, 100, "syncFileVisitor", true);
    }

    public void doSync(String recipient) {
        if (!isNotRebuildingIndex()) {
            return;
        }
        ThreadUtil.execute(() -> {
            if (!SYNC_FILE_LOCK.tryLock()) {
                return;
            }
            try {
                Path path = Paths.get(fileProperties.getRootDir());
                rebuildingIndex(recipient, path);
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
            rebuildingIndexCompleted();
            if (this.recipient == null) {
                this.recipient = recipient;
            }
            if (DELAY_DELETE_TAG_TIMER != null) {
                DELAY_DELETE_TAG_TIMER.cancel();
            }
            Set<FileVisitOption> fileVisitOptions = EnumSet.noneOf(FileVisitOption.class);
            fileVisitOptions.add(FileVisitOption.FOLLOW_LINKS);
            // 添加删除标记, 扫描完后如果标记还在则删除
            addDeleteFlagOfDoc();
            // 重置索引状态
            resetIndexStatus();
            FileCountVisitor fileCountVisitor = new FileCountVisitor();
            Files.walkFileTree(path, fileVisitOptions, Integer.MAX_VALUE, fileCountVisitor);
            log.info("开始同步, 文件数: {}", fileCountVisitor.getCount());
            timeInterval.start();
            if (syncFileVisitor == null) {
                syncFileVisitor = new SyncFileVisitor(fileCountVisitor.getCount());
            }
            Files.walkFileTree(path, fileVisitOptions, Integer.MAX_VALUE, syncFileVisitor);
            // 同步文件完成
            REBUILDING_INDEX.set(true);
            deleteDocWithDeleteFlag();
        } catch (IOException e) {
            log.error("{}{}", e.getMessage(), path, e);
        } finally {
            syncFileVisitor = null;
            log.info("同步完成, 耗时: {}s", timeInterval.intervalSecond());
            pushMessage(this.recipient, 100, MSG_SYNCED);
            pushMessage(this.recipient, 0, RebuildIndexTaskService.MSG_INDEXING);
        }
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
        REBUILDING_INDEX.set(false);
        NOT_INDEX_TASK_SIZE.set(0);
        INDEXED_TASK_SIZE.set(0);
        indexingPercent = 0;
    }

    public void incrementNotIndexTaskSize(int size) {
        NOT_INDEX_TASK_SIZE.addAndGet(size);
    }

    public void incrementIndexedTaskSize() {
        INDEXED_TASK_SIZE.incrementAndGet();
        if (isNotSyncFile()) {
            double percent = (double) INDEXED_TASK_SIZE.get() / NOT_INDEX_TASK_SIZE.get();
            int currentPercent = (int) (percent * 100);
            if (currentPercent > indexingPercent) {
                pushMessage(null, indexingPercent, RebuildIndexTaskService.MSG_INDEXING);
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
     */
    public ResponseResult<Object> sync(String username) {
        doSync(username);
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
        if (syncFileVisitor != null && syncFileVisitor.getPercent() < 100) {
            syncPercent = syncFileVisitor.getPercent();
        }
        percentMap.put("syncPercent", syncPercent);

        int indexingPercent = this.indexingPercent;
        percentMap.put("indexingPercent", indexingPercent);
        return percentMap;
    }

    private class SyncFileVisitor extends SimpleFileVisitor<Path> {

        private final double totalCount;

        @Getter
        private int percent = 0;

        private final AtomicLong processCount;

        public SyncFileVisitor(double totalCount) {
            this.totalCount = totalCount;
            this.processCount = new AtomicLong(0);
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
            executorService.execute(() -> createFile(username, dir));
            return super.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            String username = commonFileService.getUsernameByAbsolutePath(file);
            if (StrUtil.isBlank(username)) {
                return super.visitFile(file, attrs);
            }
            executorService.execute(() -> createFile(username, file));
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
                if (totalCount > 0) {
                    if (processCount.get() <= 2) {
                        pushMessage(recipient, 1, MSG_SYNCED);
                    }
                    processCount.addAndGet(1);
                    int currentPercent = (int) (processCount.get() / totalCount * 100);
                    if (currentPercent > percent) {
                        pushMessage(recipient, currentPercent, MSG_SYNCED);
                    }
                    percent = currentPercent;
                }
            }
        }
    }

    public void pushMessage(String recipient, int percent, String message) {
        if (StrUtil.isBlank(recipient)) {
            recipient = this.recipient;
        }
        commonFileService.pushMessage(recipient, percent, message);
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
    private void addDeleteFlagOfDoc() {
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where("alonePage").exists(false));
        query.addCriteria(Criteria.where("release").exists(false));
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
        query.addCriteria(Criteria.where("_id").in(fileIdList).and("delete").is(1));
        Update update = new Update();
        update.unset("delete");
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
    }

    @PreDestroy
    public void destroy() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

}

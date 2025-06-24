package com.jmal.clouddisk.lucene;

import cn.hutool.core.io.CharsetDetector;
import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileIndex;
import com.jmal.clouddisk.model.FileIntroVO;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.util.FileContentTypeUtils;
import com.jmal.clouddisk.util.MyFileUtils;
import com.mongodb.client.AggregateIterable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.mozilla.universalchardet.UniversalDetector;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.jmal.clouddisk.service.impl.CommonFileService.COLLECTION_NAME;

/**
 * @author jmal
 * <p>
 * 优化索引
 * indexWriter.forceMerge(1);
 * indexWriter.commit();
 * @Description LuceneService
 * @Date 2021/4/27 4:44 下午
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LuceneService {

    private final FileProperties fileProperties;
    private final MongoTemplate mongoTemplate;
    private final IndexWriter indexWriter;
    private final IUserService userService;
    private final ReadContentService readContentService;
    private final RebuildIndexTaskService rebuildIndexTaskService;
    private final SearchFileService searchFileService;
    private final EtagService esTagService;

    public static final String MONGO_INDEX_FIELD = "index";

    /**
     * 创建索引线程池
     */
    private ExecutorService executorCreateIndexService;
    /**
     * 更新索引内容线程池
     */
    private ExecutorService executorUpdateContentIndexService;
    /**
     * 更新大文件索引内容线程池
     */
    private ExecutorService executorUpdateBigContentIndexService;

    /**
     * 定时任务线程池
     */
    ScheduledExecutorService scheduler;

    /**
     * 新建索引文件缓冲队列大小
     */
    private static final int CREATE_INDEX_QUEUE_SIZE = 40960;

    /**
     * 新建索引文件缓冲队列
     */
    private ArrayBlockingQueue<String> indexFileQueue;

    /**
     * 新建索引文件内容缓冲队列
     */
    private ArrayBlockingQueue<String> indexFileContentQueue;

    private final AtomicBoolean processingUnIndexedScheduled = new AtomicBoolean(false);

    public static final String FIELD_CONTENT_NGRAM = "content_ngram";
    public static final String FIELD_FILENAME_NGRAM = "filename_ngram";
    public static final String FIELD_TAG_NAME_NGRAM = "tagName_ngram";

    public static final String FIELD_CONTENT_FUZZY = "content";
    public static final String FIELD_FILENAME_FUZZY = "filename";
    public static final String FIELD_TAG_NAME_FUZZY = "tagName";

    // 定义分段大小
    private static final int CHUNK_SIZE_CHARS = 1024; // 例如，每 1KB 左右一个块，或者按行数
    private static final int CHUNK_OVERLAP_CHARS = 7; // 段落间的重叠字符数，防止边界切割问题 (NGramTokenFilter本身可能处理部分边界，但显式重叠更保险)

    @PostConstruct
    public void init() {
        if (executorCreateIndexService == null) {
            int processors = Runtime.getRuntime().availableProcessors() - 2;
            executorCreateIndexService = ThreadUtil.newFixedExecutor(Math.max(processors, 3), 100, "createIndexFileTask", true);
        }
        // 获取jvm可用内存
        long maxMemory = Runtime.getRuntime().maxMemory();
        // 获取可用处理器数量
        int smallProcessors = Runtime.getRuntime().availableProcessors() - 3;
        if (executorUpdateContentIndexService == null) {
            // 设置线程数, 假设每个线程占用内存为500M
            int maxSmallProcessors = (int) ((maxMemory / 1024 / 1024) / 500);
            if (smallProcessors > maxSmallProcessors) {
                smallProcessors = maxSmallProcessors;
            }
            smallProcessors = Math.max(smallProcessors, 1);
            executorUpdateContentIndexService = ThreadUtil.newFixedExecutor(smallProcessors, 20, "updateContentIndexTask", true);
        }
        if (executorUpdateBigContentIndexService == null) {
            // 设置线程数, 假设每个线程占用内存为2G
            int bigProcessors = Math.toIntExact(maxMemory / 1024 / 1024 / 4096);
            bigProcessors = Math.max(bigProcessors, 1);
            executorUpdateBigContentIndexService = ThreadUtil.newFixedExecutor(bigProcessors, 100, "updateBigContentIndexTask", true);
        }
        log.info("NGRAM_MAX_CONTENT_LENGTH_MB:{}, NGRAM_MIN_SIZE: {}, ngramMaxSize: {}", fileProperties.getNgramMaxContentLengthMB(), fileProperties.getNgramMinSize(), fileProperties.getNgramMaxSize());
    }

    /**
     * 推送至新建索引文件缓存队列
     *
     * @param fileId fileId
     */
    public void pushCreateIndexQueue(String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            return;
        }
        try {
            getIndexFileQueue().put(fileId);
        } catch (InterruptedException e) {
            log.error("推送新建索引队列失败, fileId: {}, {}", fileId, e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 推送至新建索引文件缓存队列
     *
     * @param fileId fileId
     */
    public void pushCreateIndexContentQueue(String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            return;
        }
        try {
            getIndexFileContentQueue().put(fileId);
        } catch (InterruptedException e) {
            log.error("推送新建索引内容队列失败, fileId: {}, {}", fileId, e.getMessage(), e);
            Thread.currentThread().interrupt();
        }
    }

    private ArrayBlockingQueue<String> getIndexFileQueue() {
        if (indexFileQueue == null) {
            indexFileQueue = new ArrayBlockingQueue<>(CREATE_INDEX_QUEUE_SIZE);
            if (scheduler == null) {
                scheduler = new ScheduledThreadPoolExecutor(1, ThreadUtil.createThreadFactory("luceneScheduler"));
            }
            createIndexFileTask();
        }
        return indexFileQueue;
    }

    private ArrayBlockingQueue<String> getIndexFileContentQueue() {
        if (indexFileContentQueue == null) {
            indexFileContentQueue = new ArrayBlockingQueue<>(CREATE_INDEX_QUEUE_SIZE);
        }
        return indexFileContentQueue;
    }

    /**
     * 新建索引文件任务
     */
    private void createIndexFileTask() {
        getIndexFileQueue();
        getIndexFileContentQueue();
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<String> fileIdList = new ArrayList<>(indexFileQueue.size());
                List<String> toBeIndexedFileIdList = new ArrayList<>(indexFileContentQueue.size());
                executorCreateIndexService.execute(() -> {
                    // 添加待索引标记
                    indexFileContentQueue.drainTo(toBeIndexedFileIdList);
                    addToBeIndexedFlagOfDoc(toBeIndexedFileIdList);

                    // 添加不带文件内容的索引
                    indexFileQueue.drainTo(fileIdList);
                    if (!fileIdList.isEmpty()) {
                        createIndexFiles(fileIdList);
                    }
                });
                // 更新任务进度
                rebuildIndexTaskService.updateTaskProgress();
            } catch (Exception e) {
                log.error("创建索引失败", e);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * 创建索引
     *
     * @param fileIdList fileIdList
     */
    private void createIndexFiles(List<String> fileIdList) {
        // 提取出fileIdList
        List<FileIntroVO> fileIntroVOList = searchFileService.getFileIntroVOs(fileIdList);
        for (FileIntroVO fileIntroVO : fileIntroVOList) {
            rebuildIndexTaskService.incrementNotIndexTaskSize();
            updateIndex(false, fileIntroVO);
        }
        rebuildIndexTaskService.removeDeletedFlag(fileIdList);
    }

    private void updateIndex(boolean readContent, FileIntroVO fileIntroVO) {
        try {
            File file = getFileByFileIntroVO(fileIntroVO);
            boolean isContent = checkFileContent(file);
            if (!readContent && isContent) {
                pushCreateIndexContentQueue(fileIntroVO.getId());
            }
            if (!readContent && !isContent) {
                rebuildIndexTaskService.incrementIndexedTaskSize();
            }
            FileIndex fileIndex = new FileIndex(file, fileIntroVO);
            fileIndex.setTagName(getTagName(fileIntroVO));
            setFileIndex(fileIndex);
            String content = null;
            if (readContent) {
                content = readFileContent(file, fileIntroVO.getId());
                if (CharSequenceUtil.isBlank(content)) {
                    updateIndexStatus(fileIntroVO, IndexStatus.INDEXED);
                    return;
                }
            }
            updateIndexDocument(indexWriter, fileIndex, content);
            if (CharSequenceUtil.isNotBlank(content)) {
                log.debug("添加索引, filepath: {}", file.getAbsoluteFile());
                startProcessFilesToBeIndexed();
            }
        } catch (Exception e) {
            log.warn("updateIndexError: {}", e.getMessage(), e);
        } finally {
            updateIndexStatus(fileIntroVO, IndexStatus.INDEXED);
            if (readContent) {
                rebuildIndexTaskService.incrementIndexedTaskSize();
            }
        }
    }

    /**
     * 更新索引状态
     *
     * @param fileIntroVO fileIntroVO
     * @param indexStatus IndexStatus
     */
    private void updateIndexStatus(FileIntroVO fileIntroVO, IndexStatus indexStatus) {
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where("_id").is(fileIntroVO.getId()));
        Update update = new Update();
        update.set(MONGO_INDEX_FIELD, indexStatus.getStatus());
        mongoTemplate.updateFirst(query, update, COLLECTION_NAME);

        // set etag
        String username = userService.getUserNameById(fileIntroVO.getUserId());
        esTagService.updateFileEtagAsync(username, getFileByFileIntroVO(fileIntroVO));
    }

    private File getFileByFileIntroVO(FileIntroVO fileIntroVO) {
        String username = userService.getUserNameById(fileIntroVO.getUserId());
        return Paths.get(fileProperties.getRootDir(), username, fileIntroVO.getPath(), fileIntroVO.getName()).toFile();
    }

    private String getTagName(FileIntroVO fileIntroVO) {
        if (fileIntroVO != null && fileIntroVO.getTags() != null && !fileIntroVO.getTags().isEmpty()) {
            return fileIntroVO.getTags().stream().map(Tag::getName).reduce((a, b) -> a + " " + b).orElse("");
        }
        return null;
    }

    /**
     * 构建FileIndex
     *
     * @param fileIndex FileIndex
     */
    public void setFileIndex(FileIndex fileIndex) {
        File file = fileIndex.getFile();
        if (!FileUtil.exist(fileIndex.getFile())) {
            return;
        }
        fileIndex.setIsFolder(file.isDirectory());
        fileIndex.setName(file.getName());
        try {
            fileIndex.setModified(Files.getLastModifiedTime(file.toPath()).toMillis());
        } catch (IOException e) {
            log.error("获取文件修改时间失败, file: {}, {}", file.getAbsolutePath(), e.getMessage(), e);
        }
        fileIndex.setSize(file.length());
        setType(file, fileIndex);
    }

    private void setType(File file, FileIndex fileIndex) {
        String fileName = file.getName();
        String suffix = MyFileUtils.extName(fileName);
        fileIndex.setType(Constants.OTHER);
        if (CharSequenceUtil.isBlank(suffix)) {
            fileIndex.setType(Constants.OTHER);
            return;
        }
        String contentType = FileContentTypeUtils.getContentType(suffix);
        if (CharSequenceUtil.isBlank(contentType)) {
            fileIndex.setType(Constants.OTHER);
            return;
        }
        if (contentType.startsWith(Constants.CONTENT_TYPE_IMAGE)) {
            fileIndex.setType(Constants.CONTENT_TYPE_IMAGE);
        }
        if (contentType.startsWith(Constants.VIDEO)) {
            fileIndex.setType(Constants.VIDEO);
        }
        if (contentType.startsWith(Constants.AUDIO)) {
            fileIndex.setType(Constants.AUDIO);
        }
        if (fileProperties.getDocument().contains(suffix)) {
            fileIndex.setType(Constants.DOCUMENT);
        }
    }

    private String readFileContent(File file, String fileId) {
        try {
            if (file == null) {
                return null;
            }
            if (!file.isFile() || file.length() < 1) {
                return null;
            }
            String type = FileTypeUtil.getType(file).toLowerCase();
            switch (type) {
                case "pdf" -> {
                    return readContentService.readPdfContent(file, fileId);
                }
                case "dwg" -> {
                    return readContentService.dwg2mxweb(file, fileId);
                }
                case "epub" -> {
                    return readContentService.readEpubContent(file, fileId);
                }
                case "ppt", "pptx" -> {
                    return readContentService.readPPTContent(file);
                }
                case "doc", "docx" -> {
                    return readContentService.readWordContent(file);
                }
                case "xls", "xlsx" -> {
                    return readContentService.readExcelContent(file);
                }
                default -> {
                    if (fileProperties.getSimText().contains(type)) {
                        String charset = UniversalDetector.detectCharset(file);
                        if (CharSequenceUtil.isBlank(charset)) {
                            charset = String.valueOf(CharsetDetector.detect(file, StandardCharsets.UTF_8));
                        }
                        return FileUtil.readString(file, Charset.forName(charset));
                    }
                    String contentType = CommonFileService.getContentType(file, FileContentTypeUtils.getContentType(file, MyFileUtils.extName(file.getName())));
                    if (contentType.contains("charset=utf-8")) {
                        return FileUtil.readString(file, StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception e) {
            log.error("读取文件内容失败, file: {}, {}", file.getAbsolutePath(), e.getMessage(), e);
        }
        return null;
    }

    private boolean checkFileContent(File file) {
        try {
            if (file == null) {
                return false;
            }
            if (!file.isFile() || file.length() < 1) {
                return false;
            }
            String type = FileTypeUtil.getType(file).toLowerCase();
            if (MyFileUtils.hasContentFile(type)) {
                return true;
            }
            if (fileProperties.getSimText().contains(type)) {
                return true;
            }
            return MyFileUtils.hasCharset(file);
        } catch (Exception e) {
            return false;
        }
    }


    public void deleteIndexDocuments(List<String> fileIds) {
        try {
            for (String fileId : fileIds) {
                Term term = new Term("id", fileId);
                indexWriter.deleteDocuments(term);
            }
            indexWriter.commit();
        } catch (IOException e) {
            log.error("删除索引失败, fileIds: {}, {}", fileIds, e.getMessage(), e);
        }
    }

    /**
     * 添加/更新索引
     *
     * @param indexWriter indexWriter
     * @param fileIndex   FileIndex
     * @param content     content
     */
    public void updateIndexDocument(IndexWriter indexWriter, FileIndex fileIndex, String content) {
        String fileId = fileIndex.getFileId();
        try {
            String fileName = (fileIndex.getName() == null ? "" : fileIndex.getName()) + " " + fileIndex.getRemark();
            String tagName = fileIndex.getTagName();
            Boolean isFolder = fileIndex.getIsFolder();
            Boolean isFavorite = fileIndex.getIsFavorite();
            String path = fileIndex.getPath();
            org.apache.lucene.document.Document newDocument = new org.apache.lucene.document.Document();
            newDocument.add(new StringField("id", fileId, Field.Store.YES));
            newDocument.add(new StringField(IUserService.USER_ID, fileIndex.getUserId(), Field.Store.NO));
            if (fileIndex.getType() != null) {
                newDocument.add(new StringField("type", fileIndex.getType(), Field.Store.NO));
            }
            if (CharSequenceUtil.isNotBlank(fileName)) {
                newDocument.add(new Field(FIELD_FILENAME_NGRAM, fileName, TextField.TYPE_NOT_STORED));
                newDocument.add(new TextField(FIELD_FILENAME_FUZZY, fileName, Field.Store.NO));
            }
            if (isFolder != null) {
                newDocument.add(new IntPoint(Constants.IS_FOLDER, isFolder ? 1 : 0));
            }
            if (isFavorite != null) {
                newDocument.add(new IntPoint(Constants.IS_FAVORITE, isFavorite ? 1 : 0));
            }
            if (path != null) {
                newDocument.add(new StringField(Constants.PATH_FIELD, path, Field.Store.NO));
            }
            if (CharSequenceUtil.isNotBlank(tagName)) {
                newDocument.add(new Field(FIELD_TAG_NAME_NGRAM, tagName, TextField.TYPE_NOT_STORED));
                newDocument.add(new TextField(FIELD_TAG_NAME_FUZZY, tagName, Field.Store.NO));
            }
            if (CharSequenceUtil.isNotBlank(content)) {
                newDocument.add(new TextField(FIELD_CONTENT_FUZZY, content, Field.Store.NO));
            }

            // 为精确子串匹配处理 content_ngram
            if (CharSequenceUtil.isNotBlank(content) && fileProperties.getExactSearch()) {
                // 对非常大的文件，跳过N-Gram或只处理一部分
                String contentForNgram = getContentForNgram(content, fileIndex);
                if (CharSequenceUtil.isNotBlank(contentForNgram)) {
                    List<String> chunks = segmentContent(contentForNgram);
                    for (String chunk : chunks) {
                        if (CharSequenceUtil.isNotBlank(chunk)) {
                            newDocument.add(new Field(FIELD_CONTENT_NGRAM, chunk, TextField.TYPE_NOT_STORED));
                        }
                    }
                }
            }

            if (fileIndex.getModified() != null) {
                newDocument.add(new NumericDocValuesField("modified", fileIndex.getModified()));
            }
            if (fileIndex.getSize() != null) {
                newDocument.add(new NumericDocValuesField("size", fileIndex.getSize()));
            }
            indexWriter.updateDocument(new Term("id", fileId), newDocument);
        } catch (IOException e) {
            log.error("更新索引失败, fileId: {}, {}", fileId, e.getMessage(), e);
        }
    }

    private String getContentForNgram(String fullContent, FileIndex fileIndex) {
        // 示例：对于N-Gram，只取文件的前NgramMaxContentLength内容
        if (fullContent.length() > fileProperties.getNgramMaxContentLength()) {
            log.warn("截断内容以进行N-Gram索引（原始长度：{}MB，文件大小：{}MB）, 文件: {}", fullContent.length() / 1024 / 1024, fileIndex.getSize() / 1024 / 1024, fileIndex.getUsername() + fileIndex.getPath() + fileIndex.getName());
            return fullContent.substring(0, fileProperties.getNgramMaxContentLength());
        }
        return fullContent;
    }

    private List<String> segmentContent(String content) {
        List<String> chunks = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            return chunks;
        }
        int length = content.length();
        if (length <= CHUNK_SIZE_CHARS) { // 如果内容本身小于或等于块大小，则作为一个块
            chunks.add(content);
            return chunks;
        }

        // 确保步进大于0
        int step = CHUNK_SIZE_CHARS - CHUNK_OVERLAP_CHARS;

        for (int i = 0; i < length; i += step) {
            int end = Math.min(i + CHUNK_SIZE_CHARS, length);
            chunks.add(content.substring(i, end));
            if (end == length) {
                break; // 已到达内容末尾
            }
        }
        return chunks;
    }

    public FileIntroVO getFileIntroVO(String fileId) {
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        query.fields().include("id", "name", "userId", "path", "isFolder", "isFavorite", "remark", "tags", "etag", "size");
        return mongoTemplate.findOne(query, FileIntroVO.class, COLLECTION_NAME);
    }

    /**
     * 添加待索引标记
     */
    private void addToBeIndexedFlagOfDoc(List<String> fielIdList) {
        if (fielIdList.isEmpty()) {
            return;
        }
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where("_id").in(fielIdList));
        Update update = new Update();
        update.set(MONGO_INDEX_FIELD, IndexStatus.NOT_INDEX.getStatus());
        mongoTemplate.updateMulti(query, update, COLLECTION_NAME);
        // 添加待索引标记
        startProcessFilesToBeIndexed();
    }

    /**
     * 处理待索引文件
     */
    public void processFilesToBeIndexed() throws IOException {
        boolean run = true;
        log.debug("开始处理待索引文件");
        while (run) {
            if (!hasUnIndexFile()) {
                log.debug("待索引文件处理完成");
                rebuildIndexTaskService.rebuildingIndexCompleted();
                run = false;
            }
            List<org.bson.Document> pipeline = Arrays.asList(new org.bson.Document("$match", new org.bson.Document(MONGO_INDEX_FIELD, IndexStatus.NOT_INDEX.getStatus())), new org.bson.Document("$project", new org.bson.Document("_id", 1)), new org.bson.Document("$limit", 8));
            AggregateIterable<org.bson.Document> aggregateIterable = mongoTemplate.getCollection(COLLECTION_NAME).aggregate(pipeline);
            for (org.bson.Document document : aggregateIterable) {
                String fileId = document.getObjectId("_id").toHexString();
                FileIntroVO fileIntroVO = getFileIntroVO(fileId);
                if (fileIntroVO != null) {
                    // 处理待索引文件
                    updateIndexStatus(fileIntroVO, IndexStatus.INDEXING);
                    processFileThreaded(fileIntroVO);
                }
            }
        }
    }

    /**
     * 是否存在待索引文件
     */
    private boolean hasUnIndexFile() {
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where(MONGO_INDEX_FIELD).is(IndexStatus.NOT_INDEX.getStatus()));
        long count = mongoTemplate.count(query, COLLECTION_NAME);
        return count > 0;
    }

    private void processFileThreaded(FileIntroVO fileIntroVO) {
        long size = fileIntroVO.getSize();

        if (RebuildIndexTaskService.isSyncFile()) {
            // 单线程处理
            updateIndex(true, fileIntroVO);
        } else {
            // 根据文件大小选择多线程处理
            if (size > 10 * 1024 * 1024) {
                // 大文件，使用特定线程池处理
                executorUpdateBigContentIndexService.execute(() -> updateIndex(true, fileIntroVO));
            } else {
                // 小文件，使用普通线程池处理
                executorUpdateContentIndexService.execute(() -> updateIndex(true, fileIntroVO));
            }
        }
    }

    private void startProcessFilesToBeIndexed() {
        if (processingUnIndexedScheduled.compareAndSet(false, true)) {
            executorCreateIndexService.execute(() -> {
                try {
                    processFilesToBeIndexed();
                } catch (IOException e) {
                    log.error("处理待索引文件内容失败", e);
                } finally {
                    processingUnIndexedScheduled.set(false);
                }
            });
        }
    }

    @PreDestroy
    public void destroy() {
        if (executorCreateIndexService != null) {
            executorCreateIndexService.shutdown();
        }
        if (executorUpdateContentIndexService != null) {
            executorUpdateContentIndexService.shutdown();
        }
        if (executorUpdateBigContentIndexService != null) {
            executorUpdateBigContentIndexService.shutdown();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

}

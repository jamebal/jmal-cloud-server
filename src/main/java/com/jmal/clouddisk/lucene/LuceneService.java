package com.jmal.clouddisk.lucene;

import cn.hutool.core.io.CharsetDetector;
import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileIndex;
import com.jmal.clouddisk.model.file.FileIntroVO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.CommonUserService;
import com.jmal.clouddisk.util.FileContentTypeUtils;
import com.jmal.clouddisk.util.HashUtil;
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
import org.springframework.context.ApplicationListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
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
public class LuceneService implements ApplicationListener<LuceneIndexQueueEvent> {

    private final FileProperties fileProperties;
    private final MongoTemplate mongoTemplate;
    private final IndexWriter indexWriter;
    private final CommonUserService userService;
    private final ReadContentService readContentService;
    private final PopplerPdfReader popplerPdfReader;
    private final RebuildIndexTaskService rebuildIndexTaskService;
    private final SearchFileService searchFileService;
    private final LuceneQueryService luceneQueryService;
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
    public static final String FIELD_TAG_ID = "tagId";

    // 定义分段大小
    private static final int CHUNK_SIZE_CHARS = 1024; // 例如，每 1KB 左右一个块，或者按行数
    private static final int CHUNK_OVERLAP_CHARS = 7; // 段落间的重叠字符数，防止边界切割问题 (NGramTokenFilter本身可能处理部分边界，但显式重叠更保险)

    public static final int BYTES_PER_MB = 1024 * 1024;
    private static final long MEMORY_PER_SMALL_THREAD_MB = 500;
    private static final long MEMORY_PER_BIG_THREAD_MB = 4096;

    @PostConstruct
    public void init() {
        if (executorCreateIndexService == null) {
            int processors = Runtime.getRuntime().availableProcessors() - 2;
            executorCreateIndexService = ThreadUtil.newFixedExecutor(Math.max(processors, 3), 100, "createIndexFileTask", true);
        }
        // 获取jvm可用内存
        long maxMemory = Runtime.getRuntime().maxMemory();
        if (executorUpdateContentIndexService == null) {
            // 获取可用处理器数量
            int smallProcessors = Runtime.getRuntime().availableProcessors() - 3;
            // 设置线程数, 假设每个线程占用内存为500M
            int maxSmallProcessors = Math.toIntExact((maxMemory / BYTES_PER_MB) / MEMORY_PER_SMALL_THREAD_MB);
            if (smallProcessors > maxSmallProcessors) {
                smallProcessors = maxSmallProcessors;
            }
            smallProcessors = Math.max(smallProcessors, 1);
            executorUpdateContentIndexService = ThreadUtil.newFixedExecutor(smallProcessors, 20, "updateContentIndexTask", true);
        }
        if (executorUpdateBigContentIndexService == null) {
            // 设置线程数, 假设每个线程占用内存为4G
            int bigProcessors = Math.toIntExact((maxMemory / BYTES_PER_MB) / MEMORY_PER_BIG_THREAD_MB);
            bigProcessors = Math.max(bigProcessors, 1);
            executorUpdateBigContentIndexService = ThreadUtil.newFixedExecutor(bigProcessors, 100, "updateBigContentIndexTask", true);
        }
        log.info("NGRAM_MAX_CONTENT_LENGTH_MB:{}, NGRAM_MIN_SIZE: {}, ngramMaxSize: {}", fileProperties.getNgramMaxContentLengthMB(), fileProperties.getNgramMinSize(), fileProperties.getNgramMaxSize());
    }

    @Override
    public void onApplicationEvent(LuceneIndexQueueEvent event) {
        if (event.getFileId() != null) {
            pushCreateIndexQueue(event.getFileId());
        }
        if (event.getDelFileIds() != null) {
            deleteIndexDocuments(event.getDelFileIds());
        }
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
            setFileIndex(fileIndex);
            if (readContent) {
                String sha256 = HashUtil.sha256(file);
                try (StringWriter contentWriter = new StringWriter()) {
                    readFileContent(file, fileIntroVO.getId(), contentWriter);
                    String fullContent = contentWriter.toString();

                    if (CharSequenceUtil.isBlank(fullContent)) {
                        updateIndexStatus(fileIntroVO, IndexStatus.INDEXED);
                        return;
                    }
                    // 调用改造后的 updateIndexDocument
                    updateIndexDocument(indexWriter, fileIndex, fullContent, fileIndex.getFileIndexHash(sha256));
                    startProcessFilesToBeIndexed();
                }
            } else {
                updateIndexDocument(indexWriter, fileIndex, null, fileIndex.getFileIndexHash(null));
            }
            log.debug("添加索引, filepath: {}", file.getAbsoluteFile());
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
        Query query = new Query();
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

    private void readFileContent(File file, String fileId, Writer writer) {
        String charset = null;
        try {
            if (file == null || !file.isFile() || file.length() < 1) {
                return;
            }
            String type = FileTypeUtil.getType(file).toLowerCase();
            switch (type) {
                case "pdf" -> popplerPdfReader.readPdfContent(file, fileId, writer);
                case "dwg" -> readContentService.dwg2mxweb(file, fileId);
                case "epub" -> readContentService.readEpubContent(file, fileId, writer);
                case "ppt", "pptx" -> readContentService.readPPTContent(file, writer);
                case "doc", "docx" -> readContentService.readWordContent(file, writer);
                case "xls", "xlsx" -> readContentService.readExcelContent(file, writer);
                default -> {
                    if (fileProperties.getSimText().contains(type)) {
                        charset = UniversalDetector.detectCharset(file);
                        if (CharSequenceUtil.isBlank(charset)) {
                            charset = String.valueOf(CharsetDetector.detect(file, StandardCharsets.UTF_8));
                        }
                        if (CharSequenceUtil.isBlank(charset)) {
                            return;
                        }
                        // 对于纯文本，可以流式读取
                        try (Reader reader = new InputStreamReader(new FileInputStream(file), Charset.forName(charset))) {
                            reader.transferTo(writer);
                        }
                    } else {
                        String contentType = CommonFileService.getContentType(file, FileContentTypeUtils.getContentType(file, MyFileUtils.extName(file.getName())));
                        if (contentType.contains("charset=utf-8")) {
                            try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                                reader.transferTo(writer);
                            }
                        }
                    }
                }
            }
        } catch (UnsupportedCharsetException unsupportedCharsetException) {
            log.warn("不支持的字符集, charset: {}, file: {}, {}", charset, file.getAbsolutePath(), unsupportedCharsetException.getMessage());
        } catch (Exception e) {
            log.error("读取文件内容失败, file: {}, {}", file.getAbsolutePath(), e.getMessage(), e);
        }
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
     * @param fullContent fullContent
     * @param fileIndexHash fileIndexHash 唯一索引标识
     */
    public void updateIndexDocument(IndexWriter indexWriter, FileIndex fileIndex, String fullContent, String fileIndexHash) {
        String fileId = fileIndex.getFileId();
        try {
            if (luceneQueryService.existsSha256(fileIndexHash)) {
                return;
            }
            String fileName = (fileIndex.getName() == null ? "" : fileIndex.getName()) + " " + fileIndex.getRemark();
            String tagName = fileIndex.getTagName();
            Boolean isFolder = fileIndex.getIsFolder();
            Boolean isFavorite = fileIndex.getIsFavorite();
            String path = fileIndex.getPath();
            Document newDocument = new Document();
            newDocument.add(new StringField("id", fileId, Field.Store.YES));
            newDocument.add(new StringField(IUserService.USER_ID, fileIndex.getUserId(), Field.Store.NO));

            newDocument.add(new StringField(Constants.ETAG, fileIndexHash, Field.Store.NO));

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
            if (fileIndex.getTagIds() != null && !fileIndex.getTagIds().isEmpty()) {
                for (String tagId : fileIndex.getTagIds()) {
                    if (CharSequenceUtil.isNotBlank(tagId)) {
                        newDocument.add(new StringField(FIELD_TAG_ID, tagId, Field.Store.NO));
                    }
                }
            }
            if (CharSequenceUtil.isNotBlank(fullContent)) {
                newDocument.add(new TextField(FIELD_CONTENT_FUZZY, fullContent, Field.Store.NO));
            }
            if (CharSequenceUtil.isNotBlank(fullContent) && fileProperties.getExactSearch()) {
                String contentForNgram;
                // 将完整内容字符串转换为内存中的输入流
                try (InputStream contentStream = new ByteArrayInputStream(fullContent.getBytes(StandardCharsets.UTF_8))) {
                    // 调用内存高效的流式截断方法
                    contentForNgram = getContentForNgram(contentStream, fileIndex);
                } catch (IOException e) {
                    log.error("Error creating ByteArrayInputStream: {}", e.getMessage(), e);
                    return;
                }

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

    /**
     * 从输入流中高效地截取用于N-Gram的内容，避免OOM。
     *
     * @param contentStream 内容的输入流
     * @param fileIndex     文件元数据，用于日志记录
     * @return 截断后的UTF-8字符串
     * @throws IOException 读取流异常
     */
    private String getContentForNgram(InputStream contentStream, FileIndex fileIndex) throws IOException {
        int maxContentLengthInBytes = fileProperties.getNgramMaxContentLength();
        byte[] buffer = new byte[8192]; // 8KB的读取缓冲区
        int bytesRead;

        // 使用 ByteArrayOutputStream 来存储截取的结果
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream(maxContentLengthInBytes)) {
            while ((bytesRead = contentStream.read(buffer)) != -1) {
                int remainingCapacity = maxContentLengthInBytes - baos.size();
                if (bytesRead > remainingCapacity) {
                    baos.write(buffer, 0, remainingCapacity);
                    log.warn("内容已截断以进行N-Gram索引（截断至 {} 字节）, 文件大小: {}MB 文件: {}", maxContentLengthInBytes, fileIndex.getSize() / BYTES_PER_MB, Paths.get(fileIndex.getPath(), fileIndex.getName()));
                    break; // 已达到上限
                }
                baos.write(buffer, 0, bytesRead);
            }

            byte[] contentBytes = baos.toByteArray();

            // 进行UTF-8字符边界检查，防止乱码
            int effectiveLength = contentBytes.length;
            while (effectiveLength > 0 && (contentBytes[effectiveLength - 1] & 0xC0) == 0x80) {
                effectiveLength--;
            }

            if (effectiveLength == 0) {
                return "";
            }

            return new String(contentBytes, 0, effectiveLength, StandardCharsets.UTF_8);
        }
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
        Query query = new Query();
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
        Query query = new Query();
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
                rebuildIndexTaskService.delayResetIndex();
                indexWriter.commit();
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
        Query query = new Query();
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

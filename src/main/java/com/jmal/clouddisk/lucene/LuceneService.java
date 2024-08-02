package com.jmal.clouddisk.lucene;

import cn.hutool.core.io.CharsetDetector;
import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.TagService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.*;
import com.mongodb.client.AggregateIterable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.bson.types.ObjectId;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

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
    private final Analyzer analyzer;
    private final MongoTemplate mongoTemplate;
    private final IndexWriter indexWriter;
    private final SearcherManager searcherManager;
    private final IUserService userService;
    private final TagService tagService;
    private final ReadContentService readContentService;
    private final RebuildIndexTaskService rebuildIndexTaskService;

    public final static String MONGO_INDEX_FIELD = "index";
    private final UserLoginHolder userLoginHolder;

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
    private final static int CREATE_INDEX_QUEUE_SIZE = 512;

    /**
     * 新建索引文件缓冲队列
     */
    private ArrayBlockingQueue<String> indexFileQueue;

    /**
     * 新建索引文件内容缓冲队列
     */
    private ArrayBlockingQueue<String> indexFileContentQueue;

    /**
     * 处理待索引文件锁, 防止多次处理
     */
    private final ReentrantLock toBeIndexedLock = new ReentrantLock();

    @PostConstruct
    public void init() {
        if (executorCreateIndexService == null) {
            int processors = Runtime.getRuntime().availableProcessors() - 1;
            if (processors < 1) {
                processors = 1;
            }
            executorCreateIndexService = ThreadUtil.newFixedExecutor(processors, 100, "createIndexFileTask", true);
        }
        if (executorUpdateContentIndexService == null) {
            // 获取可用处理器数量
            int processors = Runtime.getRuntime().availableProcessors() - 2;
            // 获取jvm可用内存
            long maxMemory = Runtime.getRuntime().maxMemory();
            // 设置线程数, 假设每个线程占用内存为50M
            int maxProcessors = (int) (maxMemory / 50 / 1024 / 1024);
            if (processors > maxProcessors) {
                processors = maxProcessors;
            }
            if (processors < 1) {
                processors = 1;
            }
            log.info("updateContentIndexTask 线程数: {}, maxProcessors: {}", processors, maxProcessors);
            executorUpdateContentIndexService = ThreadUtil.newFixedExecutor(processors, 1, "updateContentIndexTask", true);
        }
        if (executorUpdateBigContentIndexService == null) {
            executorUpdateBigContentIndexService = ThreadUtil.newFixedExecutor(2, 100, "updateBigContentIndexTask", true);
        }
    }

    /**
     * 推送至新建索引文件缓存队列
     *
     * @param fileId fileId
     */
    public void pushCreateIndexQueue(String fileId) {
        if (StrUtil.isBlank(fileId)) {
            return;
        }
        try {
            getIndexFileQueue().put(fileId);
        } catch (InterruptedException e) {
            log.error("推送新建索引队列失败, fileId: {}, {}", fileId, e.getMessage(), e);
        }
    }

    /**
     * 推送至新建索引文件缓存队列
     *
     * @param fileId fileId
     */
    public void pushCreateIndexContentQueue(String fileId) {
        if (StrUtil.isBlank(fileId)) {
            return;
        }
        try {
            getIndexFileContentQueue().put(fileId);
        } catch (InterruptedException e) {
            log.error("推送新建索引内容队列失败, fileId: {}, {}", fileId, e.getMessage(), e);
        }
    }

    private ArrayBlockingQueue<String> getIndexFileQueue() {
        if (indexFileQueue == null) {
            indexFileQueue = new ArrayBlockingQueue<>(CREATE_INDEX_QUEUE_SIZE);
            if (scheduler == null) {
                scheduler = Executors.newScheduledThreadPool(1);
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
        ArrayBlockingQueue<String> indexFileQueue = getIndexFileQueue();
        ArrayBlockingQueue<String> indexFileContentQueue = getIndexFileContentQueue();
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
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    /**
     * 创建索引
     *
     * @param fileIdList fileIdList
     */
    private void createIndexFiles(List<String> fileIdList) {
        // 提取出fileIdList
        List<FileIntroVO> fileIntroVOList = getFileIntroVOs(fileIdList);
        for (FileIntroVO fileIntroVO : fileIntroVOList) {
            rebuildIndexTaskService.incrementNotIndexTaskSize();
            updateIndex(false, fileIntroVO);
        }
        rebuildIndexTaskService.removeDeletedFlag(fileIdList);
    }

    private void updateIndex(boolean readContent, FileIntroVO fileIntroVO) {
        String username = userService.getUserNameById(fileIntroVO.getUserId());
        File file = Paths.get(fileProperties.getRootDir(), username, fileIntroVO.getPath(), fileIntroVO.getName()).toFile();
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
            if (StrUtil.isBlank(content)) {
                rebuildIndexTaskService.incrementIndexedTaskSize();
                updateIndexStatus(fileIntroVO, IndexStatus.INDEXED);
                return;
            }
        }
        updateIndexDocument(indexWriter, fileIndex, content);
        if (StrUtil.isNotBlank(content)) {
            log.debug("添加索引, filepath: {}", file.getAbsoluteFile());
            startProcessFilesToBeIndexed();
            rebuildIndexTaskService.incrementIndexedTaskSize();
        }
        updateIndexStatus(fileIntroVO, IndexStatus.INDEXED);
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
        mongoTemplate.updateFirst(query, update, CommonFileService.COLLECTION_NAME);
    }

    private String getTagName(FileIntroVO fileDocument) {
        if (fileDocument != null && fileDocument.getTags() != null && !fileDocument.getTags().isEmpty()) {
            return fileDocument.getTags().stream().map(Tag::getName).reduce((a, b) -> a + " " + b).orElse("");
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
        String suffix = FileUtil.extName(fileName);
        fileIndex.setType(Constants.OTHER);
        if (StrUtil.isBlank(suffix)) {
            fileIndex.setType(Constants.OTHER);
            return;
        }
        String contentType = FileContentTypeUtils.getContentType(suffix);
        if (StrUtil.isBlank(contentType)) {
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
            }
            if (fileProperties.getSimText().contains(type)) {
                String charset = UniversalDetector.detectCharset(file);
                if (StrUtil.isBlank(charset)) {
                    charset = String.valueOf(CharsetDetector.detect(file, StandardCharsets.UTF_8));
                }
                return FileUtil.readString(file, Charset.forName(charset));
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
            String type = FileTypeUtil.getType(file);
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
            String fileName = fileIndex.getName();
            String tagName = fileIndex.getTagName();
            Boolean isFolder = fileIndex.getIsFolder();
            Boolean isFavorite = fileIndex.getIsFavorite();
            String path = fileIndex.getPath();
            org.apache.lucene.document.Document newDocument = new org.apache.lucene.document.Document();
            newDocument.add(new StringField("id", fileId, Field.Store.YES));
            newDocument.add(new StringField("userId", fileIndex.getUserId(), Field.Store.NO));
            if (fileIndex.getType() != null) {
                newDocument.add(new StringField("type", fileIndex.getType(), Field.Store.NO));
            }
            if (StrUtil.isNotBlank(fileName)) {
                fileName = fileName.toLowerCase();
                newDocument.add(new StringField("name", fileName, Field.Store.NO));
                newDocument.add(new TextField("content", fileName, Field.Store.NO));
            }
            if (isFolder != null) {
                newDocument.add(new IntPoint("isFolder", isFolder ? 1 : 0));
            }
            if (isFavorite != null) {
                newDocument.add(new IntPoint("isFavorite", isFavorite ? 1 : 0));
            }
            if (path != null) {
                newDocument.add(new StringField("path", path, Field.Store.NO));
            }
            if (StrUtil.isNotBlank(tagName)) {
                tagName = tagName.toLowerCase();
                newDocument.add(new StringField("tag", tagName, Field.Store.NO));
                newDocument.add(new TextField("content", tagName, Field.Store.NO));
            }
            if (StrUtil.isNotBlank(content)) {
                newDocument.add(new TextField("content", content, Field.Store.NO));
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

    public ResponseResult<List<FileIntroVO>> searchFile(SearchDTO searchDTO) {
        String keyword = searchDTO.getKeyword();
        if (keyword == null || keyword.trim().isEmpty() || searchDTO.getUserId() == null) {
            return ResultUtil.success(Collections.emptyList());
        }
        ResponseResult<List<FileIntroVO>> result = ResultUtil.genResult();
        try {
            beforeQuery(searchDTO);
            if (!searchDTO.getUserId().equals(userLoginHolder.getUserId())) {
                Map<String, Object> props = new HashMap<>();
                props.put("fileUsername", userService.getUserNameById(searchDTO.getUserId()));
                result.setProps(props);
            }

            int pageNum = searchDTO.getPage();
            int pageSize = searchDTO.getPageSize();
            searcherManager.maybeRefresh();
            List<String> seenIds = new ArrayList<>();
            IndexSearcher indexSearcher = searcherManager.acquire();
            Query query = getQuery(searchDTO);
            Sort sort = getSort(searchDTO);
            log.info("搜索关键字: {}", query.toString());
            log.info("排序规则: {}", sort);
            ScoreDoc lastScoreDoc = null;
            if (pageNum > 1) {
                int totalHitsToSkip = (pageNum - 1) * pageSize;
                TopDocs topDocs = indexSearcher.search(query, totalHitsToSkip, sort);
                if (topDocs.scoreDocs.length == totalHitsToSkip) {
                    lastScoreDoc = topDocs.scoreDocs[totalHitsToSkip - 1];
                }
            }
            int count = indexSearcher.count(query);
            TopDocs topDocs = indexSearcher.searchAfter(lastScoreDoc, query, pageSize, sort);
            for (ScoreDoc hit : topDocs.scoreDocs) {
                indexSearcher.storedFields().document(hit.doc);
                Document doc = indexSearcher.storedFields().document(hit.doc);
                String id = doc.get("id");
                seenIds.add(id);
            }
            List<FileIntroVO> fileIntroVOList = getFileIntroVOs(seenIds);
            long now = System.currentTimeMillis();
            fileIntroVOList = fileIntroVOList.parallelStream().peek(fileIntroVO -> {
                LocalDateTime updateDate = fileIntroVO.getUpdateDate();
                long update = TimeUntils.getMilli(updateDate);
                fileIntroVO.setAgoTime(now - update);
            }).toList();
            result.setData(fileIntroVOList);
            result.setCount(count);
            return result;
        } catch (IOException | ParseException | java.lang.IllegalArgumentException e) {
            log.error("搜索失败", e);
            return result.setData(Collections.emptyList()).setCount(0);
        }
    }

    /**
     * 获取排序规则
     *
     * @param searchDTO searchDTO
     * @return Sort
     */
    private static Sort getSort(SearchDTO searchDTO) {
        String sortProp = searchDTO.getSortProp();
        String sortOrder = searchDTO.getSortOrder();
        if (StrUtil.isBlank(sortProp) || StrUtil.isBlank(sortOrder)) {
            return new Sort(SortField.FIELD_SCORE);
        }
        // 创建排序规则
        SortField sortField;
        if ("updateDate".equals(searchDTO.getSortProp())) {
            sortField = new SortField("modified", SortField.Type.LONG, "descending".equalsIgnoreCase(searchDTO.getSortOrder()));
        } else if ("size".equals(searchDTO.getSortProp())) {
            sortField = new SortField("size", SortField.Type.LONG, "descending".equalsIgnoreCase(searchDTO.getSortOrder()));
        } else {
            // 默认按相关性得分排序
            sortField = SortField.FIELD_SCORE;
        }
        return new Sort(sortField);
    }

    /**
     * 构建查询器
     *
     * @param searchDTO searchDTO
     * @return Query
     * @throws ParseException ParseException
     */
    private Query getQuery(SearchDTO searchDTO) throws ParseException {
        String[] fields = {"name", "tag", "content"};
        Map<String, Float> boosts = Map.of("name", 3.0f, "tag", 2.0f, "content", 1.0f);

        // 将关键字转为小写并去掉空格
        String keyword = searchDTO.getKeyword().toLowerCase().trim();

        // 将关键字中的特殊字符转义

        keyword = StringUtil.escape(keyword);

        // 创建正则表达式查询
        BooleanQuery.Builder regexpQueryBuilder = new BooleanQuery.Builder();
        for (String field : fields) {
            if (!"content".equals(field)) {
                regexpQueryBuilder.add(new BoostQuery(new RegexpQuery(new Term(field, ".*" + keyword + ".*")), boosts.get(field)), BooleanClause.Occur.SHOULD);
            }
        }
        Query regExpQuery = new BoostQuery(regexpQueryBuilder.build(), 10.0f);

        // 对 content 字段进行完全匹配
        BooleanQuery.Builder contentQueryBuilder = new BooleanQuery.Builder();
        QueryParser contentParser = new QueryParser("content", analyzer);
        contentParser.setDefaultOperator(QueryParser.Operator.AND);
        Query contentQuery = contentParser.parse(keyword.trim());
        contentQueryBuilder.add(new BoostQuery(contentQuery, boosts.get("content")), BooleanClause.Occur.MUST);

        // 将正则表达式查询、短语查询和分词匹配查询组合成一个查询（OR关系）
        BooleanQuery combinedQuery = new BooleanQuery.Builder()
                .add(regExpQuery, BooleanClause.Occur.SHOULD)
                .add(contentQueryBuilder.build(), BooleanClause.Occur.SHOULD)
                .build();

        // 创建最终查询（AND关系）
        BooleanQuery.Builder finalQueryBuilder = new BooleanQuery.Builder()
                .add(new TermQuery(new Term(IUserService.USER_ID, searchDTO.getUserId())), BooleanClause.Occur.MUST)
                .add(combinedQuery, BooleanClause.Occur.MUST);

        // 添加其他查询条件
        otherQueryParams(searchDTO, finalQueryBuilder);

        return finalQueryBuilder.build();
    }

    /**
     * 查询前置处理
     * @param searchDTO searchDTO
     */
    private void beforeQuery(SearchDTO searchDTO) {
        String folder = searchDTO.getFolder();
        if (StrUtil.isNotBlank(folder)) {
            // 挂载点查询
            org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
            query.addCriteria(Criteria.where("_id").is(folder));
            FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class);
            if (fileDocument != null) {
                searchDTO.setCurrentDirectory(fileDocument.getPath() + fileDocument.getName());
                searchDTO.setUserId(fileDocument.getUserId());
            }
        }
    }


    private void otherQueryParams(SearchDTO searchDTO, BooleanQuery.Builder builder) {
        boolean queryPath = StrUtil.isNotBlank(searchDTO.getCurrentDirectory()) && searchDTO.getCurrentDirectory().length() > 1;
        if (queryPath) {
            Term prefixTerm = new Term("path", searchDTO.getCurrentDirectory());
            PrefixQuery prefixQuery = new PrefixQuery(prefixTerm);
            builder.add(prefixQuery, BooleanClause.Occur.MUST);
        }
        if (!queryPath && StrUtil.isNotBlank(searchDTO.getType())) {
            builder.add(new TermQuery(new Term("type", searchDTO.getType())), BooleanClause.Occur.MUST);
        }
        if (!queryPath && searchDTO.getTagId() != null) {
            TagDO tagDO = tagService.getTagInfo(searchDTO.getTagId());
            if (tagDO != null) {
                builder.add(new RegexpQuery(new Term("tag", ".*" + tagDO.getName() + ".*")), BooleanClause.Occur.MUST);
            }
        }
        if (!queryPath && searchDTO.getIsFolder() != null) {
            builder.add(IntPoint.newExactQuery("isFolder", searchDTO.getIsFolder() ? 1 : 0), BooleanClause.Occur.MUST);
        }
        if (!queryPath && searchDTO.getIsFavorite() != null) {
            builder.add(IntPoint.newExactQuery("isFavorite", searchDTO.getIsFavorite() ? 1 : 0), BooleanClause.Occur.MUST);
        }
    }

    public List<FileIntroVO> getFileIntroVOs(List<String> fileIdList) {
        List<ObjectId> objectIds = fileIdList.stream()
                .filter(ObjectId::isValid)
                .map(ObjectId::new)
                .toList();
        List<org.bson.Document> pipeline = Arrays.asList(new org.bson.Document("$match",
                        new org.bson.Document("_id",
                                new org.bson.Document("$in", objectIds))),
                new org.bson.Document("$addFields",
                        new org.bson.Document("order",
                                new org.bson.Document("$indexOfArray", Arrays.asList(objectIds, "$_id")))),
                new org.bson.Document("$sort",
                        new org.bson.Document("order", 1L)),
                new org.bson.Document("$project",
                        new org.bson.Document("order", 0L)
                                .append("contentText", 0L)));

        AggregateIterable<org.bson.Document> aggregateIterable = mongoTemplate.getCollection(CommonFileService.COLLECTION_NAME).aggregate(pipeline);

        List<FileIntroVO> results = new ArrayList<>();
        for (org.bson.Document document : aggregateIterable) {
            FileIntroVO fileIntroVO = mongoTemplate.getConverter().read(FileIntroVO.class, document);
            results.add(fileIntroVO);
        }
        return results;
    }

    public FileIntroVO getFileIntroVO(String fileId) {
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        return mongoTemplate.findOne(query, FileIntroVO.class, CommonFileService.COLLECTION_NAME);
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
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
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
            org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
            query.addCriteria(Criteria.where(MONGO_INDEX_FIELD).is(IndexStatus.NOT_INDEX.getStatus()));
            long count = mongoTemplate.count(query, CommonFileService.COLLECTION_NAME);
            if (count == 0) {
                log.debug("待索引文件处理完成");
                rebuildIndexTaskService.rebuildingIndexCompleted();
                run = false;
            }
            List<org.bson.Document> pipeline = Arrays.asList(new org.bson.Document("$match", new org.bson.Document("index", 0)), new org.bson.Document("$project", new org.bson.Document("_id", 1)), new org.bson.Document("$limit", 8));
            AggregateIterable<org.bson.Document> aggregateIterable = mongoTemplate.getCollection(CommonFileService.COLLECTION_NAME).aggregate(pipeline);
            while (aggregateIterable.iterator().hasNext()) {
                org.bson.Document document = aggregateIterable.iterator().next();
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

    private void processFileThreaded(FileIntroVO fileIntroVO) {
        long size = fileIntroVO.getSize();
        if (RebuildIndexTaskService.isSyncFile()) {
            // 单线程处理
            updateIndex(true, fileIntroVO);
        } else {
            if (RebuildIndexTaskService.isSyncFile() || size > 20 * 1024 * 1024) {
                // 大文件线程
                executorUpdateBigContentIndexService.execute(() -> updateIndex(true, fileIntroVO));
            } else {
                // 小文件线程
                executorUpdateContentIndexService.execute(() -> updateIndex(true, fileIntroVO));
            }
        }
    }

    private void startProcessFilesToBeIndexed() {
        executorCreateIndexService.execute(() -> {
            if (!toBeIndexedLock.tryLock()) {
                return;
            }
            try {
                try {
                    processFilesToBeIndexed();
                } catch (IOException e) {
                    log.error("处理待索引文件内容失败", e);
                }
            } finally {
                toBeIndexedLock.unlock();
            }
        });
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

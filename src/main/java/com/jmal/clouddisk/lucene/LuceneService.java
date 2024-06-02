package com.jmal.clouddisk.lucene;

import cn.hutool.core.io.CharsetDetector;
import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileIndex;
import com.jmal.clouddisk.model.FileIntroVO;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.TagDO;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.TagService;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author jmal
 *
 * 优化索引
 * indexWriter.forceMerge(1);
 * indexWriter.commit();
 *
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
    private final ReadPDFContentService readPDFContentService;
    private ExecutorService executorService;

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
        if (executorService == null) {
            int processors = Runtime.getRuntime().availableProcessors() - 1;
            if (processors < 1) {
                processors = 1;
            }
            executorService = ThreadUtil.newFixedExecutor(processors, 100, "createIndexFileTask", true);
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
     *
     */
    private void createIndexFileTask() {
        ArrayBlockingQueue<String> indexFileQueue = getIndexFileQueue();
        ArrayBlockingQueue<String> indexFileContentQueue = getIndexFileContentQueue();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<String> fileIdList = new ArrayList<>(indexFileQueue.size());
                List<String> toBeIndexedFileIdList = new ArrayList<>(indexFileContentQueue.size());
                executorService.execute(() -> {

                    // 添加待索引标记
                    indexFileContentQueue.drainTo(toBeIndexedFileIdList);
                    addToBeIndexedFlagOfDoc(toBeIndexedFileIdList);

                    // 添加不带文件内容的索引
                    indexFileQueue.drainTo(fileIdList);
                    if (!fileIdList.isEmpty()) {
                        createIndexFiles(fileIdList);
                    }
                });
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
            updateIndex(false, fileIntroVO);
        }
        removeDeletedFlag(fileIdList);
    }

    private void updateIndex(boolean readContent, FileIntroVO fileIntroVO) {
        String username = userService.getUserNameById(fileIntroVO.getUserId());
        File file = Paths.get(fileProperties.getRootDir(), username, fileIntroVO.getPath(), fileIntroVO.getName()).toFile();
        if (!readContent && checkFileContent(file)) {
            pushCreateIndexContentQueue(fileIntroVO.getId());
        }
        FileIndex fileIndex = new FileIndex(file, fileIntroVO);
        fileIndex.setTagName(getTagName(fileIntroVO));
        setFileIndex(fileIndex);
        String content = null;
        if (readContent) {
            content = readFileContent(file);
            if (content == null) {
                finishIndexing(fileIntroVO);
                return;
            }
        }
        updateIndexDocument(indexWriter, fileIndex, content);
        if (StrUtil.isNotBlank(content)) {
            log.info("添加索引, filepath: {}", file.getAbsoluteFile());
        }
        finishIndexing(fileIntroVO);
    }

    private void finishIndexing(FileIntroVO fileIntroVO) {
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where("_id").is(fileIntroVO.getId()));
        Update update = new Update();
        update.set("index", 2);
        mongoTemplate.updateFirst(query, update, CommonFileService.COLLECTION_NAME);
    }

    private String getTagName(FileIntroVO fileDocument) {
        if (fileDocument != null && fileDocument.getTags() != null && !fileDocument.getTags().isEmpty()) {
            return fileDocument.getTags().stream().map(Tag::getName).reduce((a, b) -> a + " " + b).orElse("");
        }
        return null;
    }

    private void removeDeletedFlag(List<String> fileIdList) {
        // 移除删除标记
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where("_id").in(fileIdList).and("delete").is(1));
        Update update = new Update();
        update.unset("delete");
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
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

    private String readFileContent(File file) {
        try {
            if (file == null) {
                return null;
            }
            if (!file.isFile() || file.length() < 1) {
                return null;
            }
            String type = FileTypeUtil.getType(file);
            if ("pdf".equals(type)) {
                return readPDFContentService.read(file);
            }
            if ("ppt".equals(type) || "pptx".equals(type)) {
                return FileContentUtil.readPPTContent(file);
            }
            if ("doc".equals(type) || "docx".equals(type)) {
                return FileContentUtil.readWordContent(file);
            }
            Charset charset = CharsetDetector.detect(file);
            if (charset == null) {
                return null;
            }
            if ("UTF-8".equals(charset.toString())) {
                if (fileProperties.getSimText().contains(type)) {
                    return FileUtil.readUtf8String(file);
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
            String type = FileTypeUtil.getType(file);
            if (MyFileUtils.hasContentFile(type)) return true;
            Charset charset = CharsetDetector.detect(file);
            if (charset == null) {
                return false;
            }
            if ("UTF-8".equals(charset.toString())) {
                if (fileProperties.getSimText().contains(type)) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
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
     * @param indexWriter indexWriter
     * @param fileIndex FileIndex
     * @param content content
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
                newDocument.add(new StringField("name", fileName.toLowerCase(), Field.Store.NO));
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
                newDocument.add(new StringField("tag", tagName.toLowerCase(), Field.Store.NO));
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
        try {
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
            return ResultUtil.success(fileIntroVOList).setCount(count);
        } catch (IOException | ParseException e) {
            log.error("搜索失败", e);
        }

        return ResultUtil.success(new ArrayList<>());
    }

    /**
     * 获取排序规则
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

        // 将关键字转为小写并去掉空格和特殊字符
        String keyword = searchDTO.getKeyword().toLowerCase().trim().replaceAll("[\\s\\p{Punct}]+", " ");

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

        AggregateIterable<org.bson.Document> aggregateIterable = mongoTemplate.getCollection(CommonFileService.COLLECTION_NAME)
                .aggregate(pipeline)
                .allowDiskUse(true);

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

    public boolean checkIndexExists() {
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where("ossFolder").exists(true));
        long count = mongoTemplate.count(query, CommonFileService.COLLECTION_NAME);
        long indexCount = indexWriter.getDocStats().numDocs;
        return indexCount > count;
    }

    public void deleteAllIndex(String userId) {
        try {
            // 创建一个Term来指定userId字段和要删除的具体userId
            Term term = new Term(IUserService.USER_ID, userId);
            // 删除所有匹配此Term的文档
            indexWriter.deleteDocuments(term);
            // 提交更改
            indexWriter.commit();
            addDeleteFlagOfDoc(userId);
            log.info("所有userId为 {} 的索引已被删除", userId);
        } catch (IOException e) {
            log.error("删除索引失败, userId: {}, {}", userId, e.getMessage(), e);
        }
    }

    /**
     * 添加删除标记
     * @param userId userId
     */
    private void addDeleteFlagOfDoc(String userId) {
        org.springframework.data.mongodb.core.query.Query query = new org.springframework.data.mongodb.core.query.Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("alonePage").exists(false));
        query.addCriteria(Criteria.where("release").exists(false));
        Update update = new Update();
        // 添加删除标记用于在之后删除
        update.set("delete", 1);
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
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
        // 添加索引标记用于在之后创建文件内容索引, 0表示待索引, 1表示已索引
        update.set("index", 0);
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
        log.debug("添加待索引标记, fileIds: {}", fielIdList);
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
            query.addCriteria(Criteria.where("index").is(0));
            long count = mongoTemplate.count(query, CommonFileService.COLLECTION_NAME);
            if (count == 0) {
                log.debug("处理待索引文件完成");
                indexWriter.commit();
                run = false;
            }
            List<org.bson.Document> pipeline = Arrays.asList(new org.bson.Document("$match", new org.bson.Document("index", 0)), new org.bson.Document("$project", new org.bson.Document("_id", 1)), new org.bson.Document("$limit", 8));
            AggregateIterable<org.bson.Document> aggregateIterable = mongoTemplate.getCollection(CommonFileService.COLLECTION_NAME).aggregate(pipeline).allowDiskUse(true);
            while (aggregateIterable.iterator().hasNext()) {
                org.bson.Document document = aggregateIterable.iterator().next();
                String fileId = document.getObjectId("_id").toHexString();
                FileIntroVO fileIntroVO = getFileIntroVO(fileId);
                if (fileIntroVO != null) {
                    updateIndex(true, fileIntroVO);
                }
            }
        }
    }

    private void startProcessFilesToBeIndexed() {
        executorService.execute(() -> {
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
        if (executorService != null) {
            executorService.shutdown();
        }
    }

}

package com.jmal.clouddisk.service.impl;

import cn.hutool.core.io.CharsetDetector;
import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileIndex;
import com.jmal.clouddisk.model.FileIntroVO;
import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.TagDO;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.util.*;
import com.mongodb.client.AggregateIterable;
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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    /**
     * 新建索引文件缓冲队列大小
     */
    private final static int CREATE_INDEX_QUEUE_SIZE = 256;

    /**
     * 新建索引文件缓冲队列
     */
    private ArrayBlockingQueue<String> indexFileQueue;

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
            ArrayBlockingQueue<String> indexFileQueue = getIndexFileQueue();
            indexFileQueue.put(fileId);
        } catch (InterruptedException e) {
            log.error("推送新建索引队列失败, fileId: {}, {}", fileId, e.getMessage(), e);
        }
    }

    private ArrayBlockingQueue<String> getIndexFileQueue() {
        if (indexFileQueue == null) {
            indexFileQueue = new ArrayBlockingQueue<>(CREATE_INDEX_QUEUE_SIZE);
            createIndexFileTask();
        }
        return indexFileQueue;
    }

    /**
     * 新建索引文件任务
     *
     */
    private void createIndexFileTask() {
        ArrayBlockingQueue<String> indexFileQueue = getIndexFileQueue();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<String> fileIdList = new ArrayList<>(indexFileQueue.size());
                indexFileQueue.drainTo(fileIdList);
                if (!fileIdList.isEmpty()) {
                    createIndexFiles(fileIdList);
                }
            } catch (Exception e) {
                log.error("创建索引失败", e);
            }
        }, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * 创建索引
     * @param fileIdList fileIdList
     * @throws IOException IOException
     */
    private void createIndexFiles(List<String> fileIdList) throws IOException {
        // 提取出fileIdList
        List<FileIntroVO> fileIntroVOList = getFileIntroVOs(fileIdList);
        for (FileIntroVO fileIntroVO : fileIntroVOList) {
            String username = userService.getUserNameById(fileIntroVO.getUserId());
            File file = Paths.get(fileProperties.getRootDir(), username, fileIntroVO.getPath(), fileIntroVO.getName()).toFile();
            FileIndex fileIndex = new FileIndex(file, fileIntroVO);
            fileIndex.setTagName(getTagName(fileIntroVO));
            setFileIndex(fileIndex);
            String content = readFileContent(file);
            updateIndexDocument(indexWriter, fileIndex, content);
            if (StrUtil.isNotBlank(content)) {
                log.info("添加索引, filepath: {}", file.getAbsoluteFile());
            }
        }
        indexWriter.commit();
        removeDeletedFlag(fileIdList);
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
                return FileContentUtil.readPdfContent(file);
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
        Map<String, Float> boosts = new HashMap<>();
        boosts.put("name", 3.0f);
        boosts.put("tag", 2.0f);
        boosts.put("content", 1.0f);

        // 将关键字转为小写
        String keyword = searchDTO.getKeyword().toLowerCase().trim();

        BooleanQuery.Builder regexpQueryBuilder = new BooleanQuery.Builder();
        for (String field : fields) {
            if ("content".equals(field)) {
                continue;
            }
            regexpQueryBuilder.add(new BoostQuery(new RegexpQuery(new Term(field, ".*" + keyword + ".*")), boosts.get(field)), BooleanClause.Occur.SHOULD);
            // regexpQueryBuilder.add(new BoostQuery(new WildcardQuery(new Term("field", "*" + keyword + "*")), boosts.get(field)), BooleanClause.Occur.SHOULD);
        }
        Query regExpQuery = regexpQueryBuilder.build();
        BoostQuery boostedRegExpQuery = new BoostQuery(regExpQuery, 10.0f);

        BooleanQuery.Builder phraseQueryBuilder = new BooleanQuery.Builder();
        for (String field : fields) {
            PhraseQuery.Builder builder = new PhraseQuery.Builder();
            // 去掉关键字中的空格和特殊字符
            keyword = keyword.replaceAll("[\\s\\p{Punct}]+", " ");
            builder.add(new Term(field, keyword.trim()));
            Query phraseQuery = builder.build();
            phraseQueryBuilder.add(new BoostQuery(phraseQuery, boosts.get(field)), BooleanClause.Occur.SHOULD);
        }
        Query phraseQuery = phraseQueryBuilder.build();

        // 创建分词匹配查询
        BooleanQuery.Builder tokensQueryBuilder = new BooleanQuery.Builder();
        for (String field : fields) {
            QueryParser parser = new QueryParser(field, analyzer);
            parser.setDefaultOperator(QueryParser.Operator.OR);
            // 去掉关键字中的空格和特殊字符
            keyword = keyword.replaceAll("[\\s\\p{Punct}]+", " ");
            Query query = parser.parse(keyword.trim());
            tokensQueryBuilder.add(new BoostQuery(query, boosts.get(field)), BooleanClause.Occur.SHOULD);
        }
        Query tokensQuery = tokensQueryBuilder.build();
        // 将正则表达式查询、短语查询和分词匹配查询组合成一个查询（OR关系）
        BooleanQuery.Builder combinedQueryBuilder = new BooleanQuery.Builder();
        combinedQueryBuilder.add(boostedRegExpQuery, BooleanClause.Occur.SHOULD);
        combinedQueryBuilder.add(phraseQuery, BooleanClause.Occur.SHOULD);
        combinedQueryBuilder.add(tokensQuery, BooleanClause.Occur.SHOULD);
        Query combinedQuery = combinedQueryBuilder.build();
        // 创建最终查询（AND关系）
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new TermQuery(new Term(IUserService.USER_ID, searchDTO.getUserId())), BooleanClause.Occur.MUST);
        // 添加其他查询条件
        otherQueryParams(searchDTO, builder);
        // 添加组合查询
        builder.add(combinedQuery, BooleanClause.Occur.MUST);

        return builder.build();

    }

    private void otherQueryParams(SearchDTO searchDTO, BooleanQuery.Builder builder) {
        if (StrUtil.isNotBlank(searchDTO.getType())) {
            builder.add(new TermQuery(new Term("type", searchDTO.getType())), BooleanClause.Occur.MUST);
        }
        if (searchDTO.getTagId() != null) {
            TagDO tagDO = tagService.getTagInfo(searchDTO.getTagId());
            if (tagDO != null) {
                builder.add(new TermQuery(new Term("tag", tagDO.getName())), BooleanClause.Occur.MUST);
            }
        }
        if (StrUtil.isNotBlank(searchDTO.getCurrentDirectory()) && searchDTO.getCurrentDirectory().length() > 1) {
            Term prefixTerm = new Term("path", searchDTO.getCurrentDirectory());
            PrefixQuery prefixQuery = new PrefixQuery(prefixTerm);
            builder.add(prefixQuery, BooleanClause.Occur.MUST);
        }
        if (searchDTO.getIsFolder() != null) {
            builder.add(IntPoint.newExactQuery("isFolder", searchDTO.getIsFolder() ? 1 : 0), BooleanClause.Occur.MUST);
        }
        if (searchDTO.getIsFavorite() != null) {
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
        Update update = new Update();
        update.set("delete", 1);
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
    }

}

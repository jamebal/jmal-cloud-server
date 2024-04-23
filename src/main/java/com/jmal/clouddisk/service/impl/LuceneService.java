package com.jmal.clouddisk.service.impl;

import cn.hutool.core.io.CharsetDetector;
import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileIndex;
import com.jmal.clouddisk.model.FileIntroVO;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.util.*;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;

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

    /**
     * 每个用户的IndexWriter
     * key: username
     * value: IndexWriter
     */
    private final Map<String, IndexWriter> indexWriterMap = new ConcurrentHashMap<>();
    /**
     * 每个用户的SearcherManager
     * key: username
     * value: SearcherManager
     */
    private final Map<String, SearcherManager> searcherManagerMap = new ConcurrentHashMap<>();

    /**
     * 新建索引文件缓冲队列
     * key: username
     * value: ArrayBlockingQueue
     */
    private final static Map<String, ArrayBlockingQueue<FileIndex>> createIndexQueueMap = new ConcurrentHashMap<>();

    /**
     * 新建索引文件缓冲队列大小
     */
    private final static int CREATE_INDEX_QUEUE_SIZE = 256;

    /**
     * 每个用户的新建索引任务线程
     * key: username
     * value: Thread
     */
    private final static Map<String, ScheduledExecutorService> createIndexThreadMap = new ConcurrentHashMap<>();

    /**
     * 获取Lucene索引目录
     *
     * @param username username
     * @return Lucene索引目录
     */
    private String getLuceneIndexDir(String username) {
        // 视频文件缓存目录
        String luceneIndexDir = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, fileProperties.getLuceneIndexDir()).toString();
        if (!FileUtil.exist(luceneIndexDir)) {
            FileUtil.mkdir(luceneIndexDir);
        }
        return luceneIndexDir;
    }

    private IndexWriter getIndexWriter(String username) throws IOException {
        String luceneIndexDir = getLuceneIndexDir(username);
        if (!indexWriterMap.containsKey(username)) {
            IndexWriter indexWriter = new IndexWriter(FSDirectory.open(Paths.get(luceneIndexDir)), new IndexWriterConfig(analyzer));
            this.indexWriterMap.put(username, indexWriter);
        }
        return indexWriterMap.get(username);
    }

    private ArrayBlockingQueue<FileIndex> getIndexFileQueue(String username) {
        if (!createIndexQueueMap.containsKey(username)) {
            ArrayBlockingQueue<FileIndex> indexFileQueue = new ArrayBlockingQueue<>(CREATE_INDEX_QUEUE_SIZE);
            createIndexQueueMap.put(username, indexFileQueue);
            createIndexFileTask(username);
        }
        return createIndexQueueMap.get(username);
    }

    private SearcherManager getSearcherManager(String username) throws IOException {
        if (!searcherManagerMap.containsKey(username)) {
            IndexWriter indexWriter = getIndexWriter(username);
            SearcherManager searcherManager = new SearcherManager(indexWriter, false, false, new SearcherFactory());
            this.searcherManagerMap.put(username, searcherManager);
        }
        return searcherManagerMap.get(username);
    }

    /**
     * 推送至新建索引文件缓存队列
     *
     * @param username username
     * @param fileId  fileId
     * @param file    File
     */
    public void pushCreateIndexQueue(String username, String fileId, File file, String tagName) {
        if (StrUtil.isBlank(fileId) || StrUtil.isBlank(username)) {
            return;
        }
        FileIndex fileIndex = getFileIndex(fileId, file);
        if (file == null) {
            fileIndex = new FileIndex(fileId);
        }
        if (fileIndex == null) {
            return;
        }
        if (StrUtil.isNotBlank(tagName)) {
            fileIndex.setTagName(tagName);
        }
        try {
            ArrayBlockingQueue<FileIndex> indexFileQueue = getIndexFileQueue(username);
            indexFileQueue.put(fileIndex);
        } catch (InterruptedException e) {
            log.error("推送新建索引队列失败, fileId: {}, {}", fileId, e.getMessage(), e);
        }
    }

    public void pushCreateIndexQueue(String username, String fileId, String tagName) {
        pushCreateIndexQueue(username, fileId, null, tagName);
    }

    /**
     * 新建索引文件任务
     *
     * @param username username
     */
    private void createIndexFileTask(String username) {
        if (!createIndexThreadMap.containsKey(username)) {
            ArrayBlockingQueue<FileIndex> indexFileQueue = getIndexFileQueue(username);
            ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
            scheduler.scheduleAtFixedRate(() -> {
                try {
                    List<FileIndex> files = new ArrayList<>(indexFileQueue.size());
                    indexFileQueue.drainTo(files);
                    if (!files.isEmpty()) {
                        createIndexFiles(username, files);
                    }
                } catch (Exception e) {
                    log.error("创建索引失败", e);
                }
            }, 0, 1, TimeUnit.SECONDS);
            createIndexThreadMap.put(username, scheduler);
        }
    }

    /**
     * 创建索引
     * @param username username
     * @param fileIndexList List<FileIndex>
     * @throws IOException IOException
     */
    private void createIndexFiles(String username, List<FileIndex> fileIndexList) throws IOException {
        IndexWriter indexWriter = getIndexWriter(username);
        for (FileIndex fileIndex : fileIndexList) {
            File file = fileIndex.getFile();
            String content = readFileContent(file);
            updateIndexDocument(indexWriter, fileIndex, content);
            if (StrUtil.isNotBlank(content)) {
                log.info("添加索引, filepath: {}", file.getAbsoluteFile());
            }
        }
        indexWriter.commit();
    }

    /**
     * 构建FileIndex
     *
     * @param fileId fileId
     * @param file file
     * @return boolean
     */
    public FileIndex getFileIndex(String fileId, File file) {
        if (!FileUtil.exist(file) || StrUtil.isBlank(fileId)) {
            return null;
        }
        FileIndex fileIndex = new FileIndex(file, fileId);
        fileIndex.setName(file.getName());
        fileIndex.setModified(file.lastModified());
        fileIndex.setSize(file.length());
        setType(file, fileIndex);
        if (file.isFile()) {
            Charset charset = CharsetDetector.detect(file);
            if (charset == null) {
                return fileIndex;
            }
            if ("UTF-8".equals(charset.toString())) {
                fileIndex.setContent(true);
            }
        }
        return fileIndex;
    }

    private void setType(File file, FileIndex fileIndex) {
        String fileName = file.getName();
        String suffix = FileUtil.extName(fileName);
        if (StrUtil.isBlank(suffix)) {
            fileIndex.setType(Constants.OTHER);
            return;
        }
        String contentType = FileContentTypeUtils.getContentType(suffix);
        if (StrUtil.isBlank(suffix)) {
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


    public void createTagIndex(String userId, String username) throws IOException {
        log.info("user: {}, 创建标签索引...", username);
        IndexWriter indexWriter = getIndexWriter(username);
        createTagIndex(userId, indexWriter);
        log.info("user: {}, 创建标签索引成功", username);
    }

    public void deleteIndexDocuments(String username, List<String> fileIds) {
        try {
            IndexWriter indexWriter = getIndexWriter(username);
            for (String fileId : fileIds) {
                Term term = new Term("id", fileId);
                indexWriter.deleteDocuments(term);
            }
            indexWriter.commit();
        } catch (IOException e) {
            log.error("删除索引失败, fileIds: {}, {}", fileIds, e.getMessage(), e);
        }
    }

    public void createTagIndex(String userId, IndexWriter indexWriter) {
        List<org.bson.Document> list = Arrays.asList(new org.bson.Document("$match",
                        new org.bson.Document("tags",
                                new org.bson.Document("$exists", true)
                                        .append("$ne", List.of()))
                                .append("userId", userId)),
                new org.bson.Document("$project",
                        new org.bson.Document("tags", 1L)));
        AggregateIterable<org.bson.Document> result = mongoTemplate.getCollection(CommonFileService.COLLECTION_NAME).aggregate(list);
        try (MongoCursor<org.bson.Document> mongoCursor = result.iterator()) {
            while (mongoCursor.hasNext()) {
                org.bson.Document doc = mongoCursor.next();
                String fileId = doc.getObjectId("_id").toHexString();
                List<org.bson.Document> tags = doc.getList("tags", org.bson.Document.class);
                if (tags == null || tags.isEmpty()) {
                    continue;
                }
                StringBuilder stringBuilder = new StringBuilder();
                for (org.bson.Document tag : tags) {
                    stringBuilder.append(tag.getString("name")).append("\n");
                }
                FileIndex fileIndex = new FileIndex(fileId);
                fileIndex.setTagName(stringBuilder.toString());
                updateIndexDocument(indexWriter, fileIndex, null);
                try {
                    indexWriter.commit();
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                }
            }
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
            org.apache.lucene.document.Document newDocument = new org.apache.lucene.document.Document();
            newDocument.add(new StringField("id", fileId, Field.Store.YES));
            if (fileIndex.getType() != null) {
                newDocument.add(new StringField("type", fileIndex.getType(), Field.Store.NO));
            }
            if (StrUtil.isNotBlank(fileName)) {
                newDocument.add(new StringField("name", fileName, Field.Store.NO));
            }
            if (StrUtil.isNotBlank(tagName)) {
                newDocument.add(new StringField("tag", tagName, Field.Store.NO));
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

    public ResponseResult<List<FileIntroVO>> searchFile(String username, SearchDTO searchDTO) {
        String keyword = searchDTO.getKeyword();
        if (keyword == null || keyword.trim().isEmpty() || username == null) {
            return ResultUtil.success(Collections.emptyList());
        }
        int pageNum = searchDTO.getPage();
        int pageSize = searchDTO.getPageSize();
        try {
            SearcherManager searcherManager = getSearcherManager(username);
            searcherManager.maybeRefresh();
            List<String> seenIds = new ArrayList<>();
            IndexSearcher indexSearcher = searcherManager.acquire();
            Query query = getQuery(keyword);
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
            List<FileIntroVO> fileIntroVOList = getFileDocuments(seenIds);
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
     * @param keyword keyword
     * @return Query
     * @throws ParseException ParseException
     */
    private Query getQuery(String keyword) throws ParseException {
        String[] fields = {"name", "tag", "content"};
        Map<String, Float> boosts = new HashMap<>();
        boosts.put("name", 3.0f);
        boosts.put("tag", 2.0f);
        boosts.put("content", 1.0f);

        BooleanQuery.Builder regexpQueryBuilder = new BooleanQuery.Builder();
        for (String field : fields) {
            if ("content".equals(field)) {
                continue;
            }
            regexpQueryBuilder.add(new BoostQuery(new RegexpQuery(new Term(field, ".*" + keyword + ".*")), boosts.get(field)), BooleanClause.Occur.SHOULD);
        }
        Query regExpQuery = regexpQueryBuilder.build();
        BoostQuery boostedRegExpQuery = new BoostQuery(regExpQuery, 10.0f);

        BooleanQuery.Builder phraseQueryBuilder = new BooleanQuery.Builder();
        for (String field : fields) {
            PhraseQuery.Builder builder = new PhraseQuery.Builder();
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
            Query query = parser.parse(keyword.trim());
            tokensQueryBuilder.add(new BoostQuery(query, boosts.get(field)), BooleanClause.Occur.SHOULD);
        }
        Query tokensQuery = tokensQueryBuilder.build();
        // 组合完全匹配查询和分词匹配查询
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(boostedRegExpQuery, BooleanClause.Occur.SHOULD);
        builder.add(phraseQuery, BooleanClause.Occur.SHOULD);
        builder.add(tokensQuery, BooleanClause.Occur.SHOULD);

        return builder.build();

    }

    public List<FileIntroVO> getFileDocuments(List<String> files) {
        List<ObjectId> objectIds = files.stream()
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


    public void deleteAllIndex(String username) {
        try {
            IndexWriter indexWriter = getIndexWriter(username);
            indexWriter.deleteAll();
            indexWriter.commit();
            log.info("user: {}, 删除索引成功", username);
        } catch (IOException e) {
            log.error("user: {}, 删除索引失败", username, e);
        }
    }

    public static void main(String[] args) throws IOException {
        File file = new File("/Users/jmal/temp/filetest/rootpath/jmal/未命名文未命名文件未命名文件未命名文件件.drawio");
        // String content = FileUtil.readUtf8String(file);
        String content = "缤缤广场";
        Console.log(StringUtil.isContainChinese(content));
        //1.创建一个Analyzer对象
        Analyzer analyzer = new SmartChineseAnalyzer();
        //2.调用Analyzer对象的tokenStream方法获取TokenStream对象，此对象包含了所有的分词结果
        TokenStream tokenStream = analyzer.tokenStream("", content);
        //3.给tokenStream对象设置一个指针，指针在哪当前就在哪一个分词上
        CharTermAttribute charTermAttribute = tokenStream.addAttribute(CharTermAttribute.class);
        //4.调用tokenStream对象的reset方法，重置指针，不调用会报错
        tokenStream.reset();
        //5.利用while循环，拿到分词列表的结果  incrementToken方法返回值如果为false代表读取完毕  true代表没有读取完毕
        while (tokenStream.incrementToken()) {
            String word = charTermAttribute.toString();
            System.out.println(word);
        }
        //6.关闭
        tokenStream.close();
        analyzer.close();
    }

    @PreDestroy
    public void destroy() throws IOException {
        for (IndexWriter indexWriter : indexWriterMap.values()) {
            indexWriter.close();
        }
        for (SearcherManager searcherManager : searcherManagerMap.values()) {
            searcherManager.close();
        }
        for (ScheduledExecutorService service : createIndexThreadMap.values()) {
            service.shutdown();
        }
    }

}

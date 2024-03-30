package com.jmal.clouddisk.service.impl;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.io.CharsetDetector;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileIndex;
import com.jmal.clouddisk.model.FileIntroVO;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.StringUtil;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jmal
 * @Description LuceneService
 * @Date 2021/4/27 4:44 下午
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LuceneService {

    private final FileProperties fileProperties;
    private final CommonFileService commonFileService;
    private final Analyzer analyzer;

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
     * 重建索引文件缓冲队列
     * key: username
     * value: ArrayBlockingQueue
     */
    private final static Map<String, ArrayBlockingQueue<FileIndex>> rebuildIndexQueueMap = new ConcurrentHashMap<>();

    /**
     * 重建索引文件缓冲队列大小
     */
    private final static int REBUILD_INDEX_QUEUE_SIZE = 256;

    /**
     * 每个用户的重建索引任务线程
     * key: username
     * value: Thread
     */
    private final static Map<String, Thread> rebuildIndexThreadMap = new ConcurrentHashMap<>();

    // @PostConstruct
    // public void optimizeIndex() {
    //     try {
    //         IndexWriter indexWriter = getIndexWriter("jmal");
    //         indexWriter.forceMerge(1);
    //         indexWriter.commit();
    //         log.info("优化索引成功");
    //     } catch (IOException e) {
    //         log.error("优化索引失败", e);
    //     }
    // }


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
        if (!rebuildIndexQueueMap.containsKey(username)) {
            ArrayBlockingQueue<FileIndex> indexFileQueue = new ArrayBlockingQueue<>(REBUILD_INDEX_QUEUE_SIZE);
            rebuildIndexQueueMap.put(username, indexFileQueue);
            rebuildIndexFileTask(username);
        }
        return rebuildIndexQueueMap.get(username);
    }

    private SearcherManager getSearcherManager(String username) throws IOException {
        if (!searcherManagerMap.containsKey(username)) {
            IndexWriter indexWriter = getIndexWriter(username);
            SearcherManager searcherManager = new SearcherManager(indexWriter, false, false, new SearcherFactory());
            // ControlledRealTimeReopenThread<IndexSearcher> cRTReopenThead = new ControlledRealTimeReopenThread<>(indexWriter, searcherManager,
            //         5.0, 0.025);
            // cRTReopenThead.setDaemon(true);
            // //线程名称
            // cRTReopenThead.setName("IndexReader-" + username);
            // // 开启线程
            // cRTReopenThead.start();
            this.searcherManagerMap.put(username, searcherManager);
        }
        return searcherManagerMap.get(username);
    }

    /**
     * 重建索引文件任务
     * @param username username
     */
    private void rebuildIndexFileTask(String username) {
        if (!rebuildIndexThreadMap.containsKey(username)) {
            Thread thread = new Thread(() -> {
                List<FileIndex> files = new ArrayList<>(REBUILD_INDEX_QUEUE_SIZE);
                ArrayBlockingQueue<FileIndex> indexFileQueue = getIndexFileQueue(username);
                while (true) {
                    try {
                        FileIndex f = indexFileQueue.take();
                        if (files.size() < REBUILD_INDEX_QUEUE_SIZE) {
                            files.add(f);
                        } else {
                            IndexWriter indexWriter = getIndexWriter(username);
                            for (FileIndex fileIndex : files) {
                                File file = fileIndex.getFile();
                                String content = readFileContent(file);
                                String documentId = fileIndex.getFileId();
                                commonFileService.updateIndexDocument(indexWriter, documentId, file.getName(), null, content);
                            }
                            indexWriter.commit();
                            files.clear();
                        }
                    } catch (Exception e) {
                        log.error("创建索引失败", e);
                    }
                }
            });
            thread.start();
            rebuildIndexThreadMap.put(username, thread);
        }
    }

    /**
     * 更新单个文件索引
     * @param username username
     * @param file file
     */
    public void updateOneFileIndex(String username, String fileId, File file) throws IOException {
    }

    /**
     * 判断是否为非文本文件
     * @param file file
     * @return boolean
     */
    public static FileIndex getFileIndex(String fileId, File file) {
        if (!FileUtil.exist(file) || StrUtil.isBlank(fileId)) {
            return null;
        }
        FileIndex fileIndex = new FileIndex(file, fileId);
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

    private String readFileContent(File file) {
        if (!file.isFile()) {
            return null;
        }
        Charset charset = CharsetDetector.detect(file);
        if (charset == null) {
            return null;
        }
        if ("UTF-8".equals(charset.toString())) {
            return FileUtil.readUtf8String(file);
        }
        return null;
    }

    /**
     * 推送至重建索引文件缓存队列
     * @param username username
     * @param fileId fileId
     * @param file file
     * @throws InterruptedException InterruptedException
     */
    public void pushRebuildIndexQueue(String username, String fileId, File file) throws InterruptedException {
        FileIndex fileIndex = getFileIndex(fileId, file);
        if (fileIndex == null) {
            return;
        }
        ArrayBlockingQueue<FileIndex> indexFileQueue = getIndexFileQueue(username);
        indexFileQueue.put(fileIndex);
    }

    public ResponseResult<List<FileIntroVO>> searchFile(String username, SearchDTO searchDTO) throws IOException, ParseException {
        commonFileService.createTagIndex(getIndexWriter(username));
        TimeInterval timeInterval = new TimeInterval();
        String keyword = searchDTO.getKeyword();
        if (keyword == null || keyword.trim().isEmpty() || username == null) {
            return ResultUtil.success(Collections.emptyList());
        }

        int pageNum = searchDTO.getPage();
        int pageSize = searchDTO.getPageSize();

        SearcherManager searcherManager = getSearcherManager(username);

        searcherManager.maybeRefresh();
        List<String> seenIds = new ArrayList<>();

        try {
            IndexSearcher indexSearcher = searcherManager.acquire();

            String[] fields = {"name", "tag", "content"};
            Map<String, Float> boosts = new HashMap<>();
            boosts.put("name", 10.0f);
            boosts.put("tag", 5.0f);
            boosts.put("content", 2.0f);
            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer, boosts);

            // String processedKeyword = Arrays.stream(keyword.trim().split("\\s+"))
            //         .map(k -> StringUtil.escape(k) + (StringUtil.isShortStr(k) ? "*" : ""))
            //         .collect(Collectors.joining(" OR "));
            // 构建短语查询
            String processedKeyword = "\"" + StringUtil.escape(keyword.trim()) + "\"";
            Query query = parser.parse(processedKeyword);

            ScoreDoc lastScoreDoc = null;
            if (pageNum > 1) {
                int totalHitsToSkip = (pageNum - 1) * pageSize;
                TopDocs topDocs = indexSearcher.search(query, totalHitsToSkip);
                if (topDocs.scoreDocs.length == totalHitsToSkip) {
                    lastScoreDoc = topDocs.scoreDocs[totalHitsToSkip - 1];
                }
            }
            TopDocs topDocs = indexSearcher.searchAfter(lastScoreDoc, query, pageSize);
            log.info("搜索耗时0: {}ms", timeInterval.intervalMs());
            for (ScoreDoc hit : topDocs.scoreDocs) {
                Document doc = indexSearcher.doc(hit.doc);
                String id = doc.get("id");
                seenIds.add(id);
                // if (!seenIds.contains(id)) {
                //     seenIds.add(id);
                // }
            }
            log.info("搜索耗时1: {}ms", timeInterval.intervalMs());
            List<FileIntroVO> fileIntroVOList = commonFileService.getFileDocuments(seenIds);
            log.info("搜索耗时2: {}ms", timeInterval.intervalMs());
            return ResultUtil.success(fileIntroVOList).setCount(seenIds.size());
        } catch (IOException e) {
            log.error("搜索失败", e);
        }

        return ResultUtil.success(new ArrayList<>());
    }

    public static void main(String[] args) throws IOException {
        String content = "re redis token to本地端通讯需要在";
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
        while (tokenStream.incrementToken()){
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
        for (Thread thread : rebuildIndexThreadMap.values()) {
            thread.interrupt();
        }
    }

}

package com.jmal.clouddisk.lucene;

import com.jmal.clouddisk.config.FileProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * @author jmal
 * @Description Lucene Config
 * @Date 2021/4/27 4:09 下午
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class LuceneConfig {

    private final FileProperties fileProperties;

    private ControlledRealTimeReopenThread<IndexSearcher> controlledRealTimeReopenThread;
    private IndexWriter indexWriterInstance; // 用于存储 IndexWriter Bean 的实例
    private SearcherManager searcherManagerInstance; // 用于存储 SearcherManager Bean 的实例

    /**
     * 创建一个 Analyzer 实例
     */
    @Bean
    public Analyzer analyzer() {
        return new SmartChineseAnalyzer();
    }

    private Path getIndexDir() {
        return Paths.get(fileProperties.getRootDir(), fileProperties.getLuceneIndexDir());
    }

    /**
     * 索引位置
     */
    @Bean
    public Directory luceneDirectory() throws IOException {
        return FSDirectory.open(getIndexDir());
    }

    /**
     * 创建indexWriter
     * 清空索引:
     * indexWriter.deleteAll();
     * indexWriter.commit();
     *
     * @param luceneDirectory 索引位置
     * @param analyzer  Analyzer
     * @return IndexWriter
     */
    @Bean
    public IndexWriter indexWriter(Directory luceneDirectory, Analyzer analyzer) throws IOException {
        // 创建一个字段分析器映射
        Map<String, Analyzer> analyzerPerField = new HashMap<>();
        // 为 "content_exact" 字段指定 KeywordAnalyzer
        analyzerPerField.put("content_exact", new KeywordAnalyzer());
        // 其他字段使用默认的 SmartChineseAnalyzer
        // 注意：如果你还有其他字段也需要特殊处理，可以在这里添加

        // 创建 PerFieldAnalyzerWrapper
        // 第一个参数是默认分析器，当字段不在映射中时使用
        // 第二个参数是字段到分析器的映射
        PerFieldAnalyzerWrapper wrapperAnalyzer = new PerFieldAnalyzerWrapper(analyzer, analyzerPerField);

        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(wrapperAnalyzer); // 使用包装后的分析器
        indexWriterConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        this.indexWriterInstance = new IndexWriter(luceneDirectory, indexWriterConfig);
        return this.indexWriterInstance;
    }

    /**
     * SearcherManager管理
     *
     * @return SearcherManager
     */
    @Bean
    public SearcherManager searcherManager(IndexWriter indexWriter) throws IOException {
        this.searcherManagerInstance = new SearcherManager(indexWriter, false, false, new SearcherFactory());
        controlledRealTimeReopenThread = new ControlledRealTimeReopenThread<>(indexWriter, searcherManagerInstance,
                5.0, 0.025);
        controlledRealTimeReopenThread.setDaemon(true);
        //线程名称
        controlledRealTimeReopenThread.setName("NRTReopenThread");
        // 开启线程
        controlledRealTimeReopenThread.start();
        return searcherManagerInstance;
    }

    @PreDestroy
    public void destroy() {
        log.info("Shutting down Lucene components...");

        // 1. 关闭 NRT Reopen 线程 (它会尝试关闭 SearcherManager)
        if (this.controlledRealTimeReopenThread != null) {
            try {
                log.debug("Closing ControlledRealTimeReopenThread...");
                this.controlledRealTimeReopenThread.close(); // 这个close()会等待线程结束
                log.info("ControlledRealTimeReopenThread closed.");
            } catch (Exception e) { // InterruptedException or IOException
                log.error("Error closing ControlledRealTimeReopenThread: {}", e.getMessage(), e);
            }
        }

        // 2. SearcherManager 可能已经被 cRTReopenThead 关闭，但作为保险再检查一次
        //    实际上，SearcherManager.close() 也会关闭它内部的 IndexReader，
        //    但通常不会关闭外部传入的 IndexWriter。
        if (this.searcherManagerInstance != null) {
            // SearcherManager 的 close() 通常由 cRTReopenThead.close() 间接触发，
            // 但如果 cRTReopenThead 未成功初始化或启动，这里可以作为后备。
            // 然而，重复关闭可能导致问题，所以最好依赖 cRTReopenThead 的关闭。
            // 如果 cRTReopenThead.close() 确保了 SearcherManager 关闭，这里可以不显式调用。
            log.debug("SearcherManager should have been closed by NRTReopenThread.");
        }

        // 3. 最重要：关闭 IndexWriter
        //    这会提交任何挂起的更改，释放写锁，并关闭文件句柄。
        if (this.indexWriterInstance != null && this.indexWriterInstance.isOpen()) {
            try {
                log.info("Closing IndexWriter...");
                this.indexWriterInstance.close();
                log.info("IndexWriter closed.");
            } catch (IOException e) {
                log.error("Error closing IndexWriter: {}", e.getMessage(), e);
            }
        }
        log.info("Lucene components shutdown complete.");
    }
}


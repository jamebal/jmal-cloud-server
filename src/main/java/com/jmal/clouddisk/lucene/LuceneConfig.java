package com.jmal.clouddisk.lucene;

import com.jmal.clouddisk.config.FileProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.ControlledRealTimeReopenThread;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSLockFactory;
import org.apache.lucene.store.NIOFSDirectory;
import org.springframework.beans.factory.annotation.Qualifier;
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

    public static final boolean IGNORE_CASE_FOR_NGRAM = true; // 是否忽略大小写 (可调)

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
        return new NIOFSDirectory(getIndexDir(), FSLockFactory.getDefault());
    }

    /**
     * 创建一个用于精确子串匹配的 N-Gram Analyzer 实例
     * 策略: KeywordTokenizer -> LowerCaseFilter (可选) -> NGramTokenFilter
     */
    @Bean("ngramAnalyzer")
    public Analyzer ngramAnalyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tokenizer = new KeywordTokenizer(); // 将整个代码段视为一个token
                TokenStream stream = tokenizer;

                if (IGNORE_CASE_FOR_NGRAM) {
                    stream = new LowerCaseFilter(stream); // 转换为小写
                }

                // 从这个 (可能已小写的) 大 Token 中生成 N-Grams
                stream = new NGramTokenFilter(stream, fileProperties.getNgramMinSize(), fileProperties.getNgramMaxSize(), false);

                return new TokenStreamComponents(tokenizer, stream);
            }
        };
    }

    /**
     * 创建一个用于处理精确子串查询词的 Analyzer 实例
     * 策略: KeywordTokenizer -> LowerCaseFilter (可选)
     * 查询词不应该被N-Gram分解
     */
    @Bean("keywordLowercaseAnalyzer")
    public Analyzer keywordLowercaseAnalyzer() {
        return new Analyzer() {
            @Override
            protected TokenStreamComponents createComponents(String fieldName) {
                Tokenizer tokenizer = new KeywordTokenizer();
                TokenStream stream = tokenizer;
                if (IGNORE_CASE_FOR_NGRAM) { // 与 ngramAnalyzer 的大小写策略保持一致
                    stream = new LowerCaseFilter(stream);
                }
                return new TokenStreamComponents(tokenizer, stream);
            }
        };
    }

    @Bean
    public IndexWriter indexWriter(Directory directory, Analyzer analyzer, // 注入默认的 SmartChineseAnalyzer (确保bean名为 "analyzer")
                                   @Qualifier("ngramAnalyzer") Analyzer ngramAnalyzer // 注入 ngramAnalyzer
    ) throws IOException {
        Map<String, Analyzer> analyzerPerField = new HashMap<>();
        analyzerPerField.put(LuceneService.FIELD_CONTENT_NGRAM, ngramAnalyzer);
        analyzerPerField.put(LuceneService.FIELD_FILENAME_NGRAM, ngramAnalyzer);
        analyzerPerField.put(LuceneService.FIELD_TAG_NAME_NGRAM, ngramAnalyzer);
        // 如果其他字段也需要特定分析器，在这里添加

        // PerFieldAnalyzerWrapper 允许为特定字段指定分析器，其他字段使用默认分析器
        PerFieldAnalyzerWrapper wrapperAnalyzer = new PerFieldAnalyzerWrapper(analyzer, analyzerPerField);

        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(wrapperAnalyzer);
        indexWriterConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        indexWriterConfig.setRAMBufferSizeMB(64); // 设置内存缓冲区大小，单位为MB
        indexWriterConfig.setRAMPerThreadHardLimitMB(32); // 设置每个线程的内存限制，单位为MB
        this.indexWriterInstance = new IndexWriter(directory, indexWriterConfig);
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
        log.debug("Shutting down Lucene components...");

        // 1. 关闭 NRT Reopen 线程 (它会尝试关闭 SearcherManager)
        if (this.controlledRealTimeReopenThread != null) {
            try {
                log.debug("Closing ControlledRealTimeReopenThread...");
                this.controlledRealTimeReopenThread.close(); // 这个close()会等待线程结束
                log.debug("ControlledRealTimeReopenThread closed.");
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
        log.debug("Lucene components shutdown complete.");
    }
}


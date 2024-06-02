package com.jmal.clouddisk.lucene;

import com.jmal.clouddisk.config.FileProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
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

/**
 * @author jmal
 * @Description Lucene Config
 * @Date 2021/4/27 4:09 下午
 */
@Configuration
@RequiredArgsConstructor
public class LuceneConfig {

    private final FileProperties fileProperties;

    private ControlledRealTimeReopenThread<IndexSearcher> cRTReopenThead;

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
    public Directory indexDir() throws IOException {
        return FSDirectory.open(getIndexDir());
    }

    /**
     * 创建indexWriter
     * 清空索引:
     * indexWriter.deleteAll();
     * indexWriter.commit();
     *
     * @param directory 索引位置
     * @param analyzer  Analyzer
     * @return IndexWriter
     */
    @Bean
    public IndexWriter indexWriter(Directory directory, Analyzer analyzer) throws IOException {
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
        return new IndexWriter(directory, indexWriterConfig);
    }

    /**
     * SearcherManager管理
     *
     * @return SearcherManager
     */
    @Bean
    public SearcherManager searcherManager(IndexWriter indexWriter) throws IOException {
        SearcherManager searcherManager = new SearcherManager(indexWriter, false, false, new SearcherFactory());
        cRTReopenThead = new ControlledRealTimeReopenThread<>(indexWriter, searcherManager,
                5.0, 0.025);
        cRTReopenThead.setDaemon(true);
        //线程名称
        cRTReopenThead.setName("更新IndexReader线程");
        // 开启线程
        cRTReopenThead.start();
        return searcherManager;
    }

    @PreDestroy
    public void destroy() {
        cRTReopenThead.close();
    }

}


package com.jmal.clouddisk.config;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.cn.smart.SmartChineseAnalyzer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author jmal
 * @Description Lucene Config
 * @Date 2021/4/27 4:09 下午
 */
@Configuration
public class LuceneConfig {

    /**
     * 创建一个 Analyzer 实例
     */
    @Bean
    public Analyzer analyzer() {
        return new SmartChineseAnalyzer();
    }

}


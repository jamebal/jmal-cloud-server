package com.jmal.clouddisk.ai;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI配置属性类
 *
 * @author jmal
 */
@Data
@Component
@ConfigurationProperties(prefix = "jmalcloud.ai")
public class AiProperties {

    /**
     * 是否启用AI功能
     */
    private Boolean enabled = false;

    /**
     * 提供商类型 (openai/ollama)
     */
    private String provider = "openai";

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * API基础URL
     */
    private String baseUrl;

    /**
     * 聊天/补全模型名称
     */
    private String model = "gpt-4o-mini";

    /**
     * 向量模型名称
     */
    private String embeddingModel = "text-embedding-3-small";

    /**
     * 温度参数 (0.0-2.0)
     */
    private Double temperature = 0.7;

    /**
     * 最大token数
     */
    private Integer maxTokens = 2000;

    /**
     * 向量维度 (默认1536, 对应OpenAI text-embedding-3-small)
     */
    private Integer vectorDimension = 1536;

    /**
     * 是否启用向量搜索
     */
    private Boolean vectorSearchEnabled = true;

    /**
     * 是否启用文件摘要功能
     */
    private Boolean summaryEnabled = true;

    /**
     * 是否启用智能分类功能
     */
    private Boolean classificationEnabled = true;

    /**
     * 文本分块大小（字符数）
     */
    private Integer chunkSize = 500;

    /**
     * 文本分块重叠大小（字符数）
     */
    private Integer chunkOverlap = 50;

    /**
     * 摘要最大长度（字符数）
     */
    private Integer summaryMaxLength = 200;

    /**
     * 搜索返回的最大结果数
     */
    private Integer searchTopK = 10;
}

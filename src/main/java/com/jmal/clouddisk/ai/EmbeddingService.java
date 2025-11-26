package com.jmal.clouddisk.ai;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 向量生成服务
 * 调用LLM的embedding API生成向量
 *
 * @author jmal
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jmalcloud.ai", name = "enabled", havingValue = "true")
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final AiProperties aiProperties;

    /**
     * 生成文本向量
     *
     * @param text 文本内容
     * @return 向量数组
     */
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[0];
        }

        try {
            // 如果文本太长，进行分块处理
            if (text.length() > aiProperties.getChunkSize() * 10) {
                return embedLongText(text);
            }

            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            if (response != null && !response.getResults().isEmpty()) {
                return response.getResults().getFirst().getOutput();
            }
        } catch (Exception e) {
            log.error("Failed to generate embedding for text: {}", e.getMessage(), e);
        }

        return new float[0];
    }

    /**
     * 批量生成向量
     *
     * @param texts 文本列表
     * @return 向量列表
     */
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> vectors = new ArrayList<>();

        if (texts == null || texts.isEmpty()) {
            return vectors;
        }

        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(texts);
            if (response != null && response.getResults() != null) {
                response.getResults().forEach(result -> vectors.add(result.getOutput()));
            }
        } catch (Exception e) {
            log.error("Failed to generate batch embeddings: {}", e.getMessage(), e);
        }

        return vectors;
    }

    /**
     * 处理长文本，分块后取平均向量
     *
     * @param text 长文本
     * @return 平均向量
     */
    private float[] embedLongText(String text) {
        List<String> chunks = splitTextIntoChunks(text);
        List<float[]> chunkVectors = embedBatch(chunks);

        if (chunkVectors.isEmpty()) {
            return new float[0];
        }

        // 计算所有分块向量的平均值
        return averageVectors(chunkVectors);
    }

    /**
     * 将文本分割成多个块
     *
     * @param text 原始文本
     * @return 分块列表
     */
    private List<String> splitTextIntoChunks(String text) {
        List<String> chunks = new ArrayList<>();
        int chunkSize = aiProperties.getChunkSize();
        int overlap = aiProperties.getChunkOverlap();

        if (text.length() <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start += chunkSize - overlap;
        }

        return chunks;
    }

    /**
     * 计算多个向量的平均值
     *
     * @param vectors 向量列表
     * @return 平均向量
     */
    private float[] averageVectors(List<float[]> vectors) {
        if (vectors.isEmpty()) {
            return new float[0];
        }

        int dimension = vectors.getFirst().length;
        float[] average = new float[dimension];

        for (float[] vector : vectors) {
            for (int i = 0; i < dimension; i++) {
                average[i] += vector[i];
            }
        }

        for (int i = 0; i < dimension; i++) {
            average[i] /= vectors.size();
        }

        // 归一化向量
        float norm = 0;
        for (float v : average) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);

        if (norm > 0) {
            for (int i = 0; i < dimension; i++) {
                average[i] /= norm;
            }
        }

        return average;
    }

    /**
     * 获取向量维度
     *
     * @return 向量维度
     */
    public int getVectorDimension() {
        return aiProperties.getVectorDimension();
    }
}

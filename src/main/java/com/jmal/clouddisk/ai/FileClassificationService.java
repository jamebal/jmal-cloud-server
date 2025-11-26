package com.jmal.clouddisk.ai;

import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jmal.clouddisk.model.TagDTO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.TagService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 文件智能分类服务
 * 自动分类和标签建议
 *
 * @author jmal
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jmalcloud.ai", name = "enabled", havingValue = "true")
public class FileClassificationService {

    private final ChatClient chatClient;
    private final CommonFileService commonFileService;
    private final FileSummaryService fileSummaryService;
    private final TagService tagService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    /**
     * 预定义分类
     */
    public static final List<String> PREDEFINED_CATEGORIES = List.of(
            "工作文档",
            "个人照片",
            "视频媒体",
            "音乐音频",
            "代码项目",
            "学习资料",
            "财务文件",
            "其他"
    );

    private static final String CLASSIFICATION_PROMPT_TEMPLATE = """
            请分析以下文件内容，给出分类和标签建议：
            
            文件名：%s
            文件类型：%s
            内容摘要：
            %s
            
            请返回JSON格式（只返回JSON，不要其他内容）：
            {
              "category": "分类名称",
              "tags": ["标签1", "标签2", "标签3"],
              "confidence": 0.95
            }
            
            可选分类（必须从以下选择一个）：工作文档、个人照片、视频媒体、音乐音频、代码项目、学习资料、财务文件、其他
            
            注意：
            1. 标签应该简洁，每个标签不超过10个字符
            2. 最多返回5个标签
            3. confidence是置信度，范围0-1
            """;

    private static final String TAG_SUGGESTION_PROMPT_TEMPLATE = """
            请根据以下文件信息，建议适合的标签：
            
            文件名：%s
            文件类型：%s
            内容摘要：
            %s
            
            请返回JSON格式（只返回JSON，不要其他内容）：
            {
              "tags": ["标签1", "标签2", "标签3", "标签4", "标签5"]
            }
            
            注意：
            1. 标签应该简洁，每个标签不超过10个字符
            2. 返回3-5个最相关的标签
            3. 标签应该有助于后续搜索和分类
            """;

    /**
     * 分类文件
     *
     * @param fileId 文件ID
     * @return 分类结果
     */
    public FileClassificationResult classifyFile(String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            return null;
        }

        if (!Boolean.TRUE.equals(aiProperties.getClassificationEnabled())) {
            log.debug("Classification is disabled");
            return null;
        }

        try {
            // 获取文件信息
            FileDocument file = commonFileService.getById(fileId);
            if (file == null) {
                log.warn("File not found: {}", fileId);
                return null;
            }

            // 获取文件摘要
            String summary = fileSummaryService.getSummary(fileId);
            if (CharSequenceUtil.isBlank(summary)) {
                summary = fileSummaryService.generateSummary(fileId);
            }

            // 生成分类
            String prompt = String.format(CLASSIFICATION_PROMPT_TEMPLATE,
                    file.getName(),
                    file.getContentType() != null ? file.getContentType() : "未知",
                    summary != null ? summary : "无内容");

            String response = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();

            return parseClassificationResult(response);
        } catch (Exception e) {
            log.error("Failed to classify file {}: {}", fileId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 建议标签
     *
     * @param fileId 文件ID
     * @return 标签建议列表
     */
    public List<String> suggestTags(String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            return new ArrayList<>();
        }

        try {
            // 获取文件信息
            FileDocument file = commonFileService.getById(fileId);
            if (file == null) {
                log.warn("File not found: {}", fileId);
                return new ArrayList<>();
            }

            // 获取文件摘要
            String summary = fileSummaryService.getSummary(fileId);
            if (CharSequenceUtil.isBlank(summary)) {
                summary = fileSummaryService.generateSummary(fileId);
            }

            // 生成标签建议
            String prompt = String.format(TAG_SUGGESTION_PROMPT_TEMPLATE,
                    file.getName(),
                    file.getContentType() != null ? file.getContentType() : "未知",
                    summary != null ? summary : "无内容");

            String response = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();

            return parseTagSuggestions(response);
        } catch (Exception e) {
            log.error("Failed to suggest tags for file {}: {}", fileId, e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 自动打标签
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 是否成功
     */
    public boolean autoTagFile(String fileId, String userId) {
        try {
            List<String> suggestedTags = suggestTags(fileId);

            if (suggestedTags.isEmpty()) {
                return false;
            }

            // 将建议的标签转换为TagDTO
            List<TagDTO> tagDTOList = suggestedTags.stream()
                    .map(tagName -> {
                        TagDTO dto = new TagDTO();
                        dto.setName(tagName);
                        return dto;
                    })
                    .toList();

            // 使用TagService添加标签
            tagService.getTagIdsByTagDTOList(tagDTOList, userId);

            log.info("Auto-tagged file {} with {} tags", fileId, suggestedTags.size());
            return true;
        } catch (Exception e) {
            log.error("Failed to auto-tag file {}: {}", fileId, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 批量分类
     *
     * @param fileIds 文件ID列表
     * @return 分类结果列表
     */
    public List<FileClassificationResult> batchClassify(List<String> fileIds) {
        List<FileClassificationResult> results = new ArrayList<>();

        if (fileIds == null || fileIds.isEmpty()) {
            return results;
        }

        // 异步批量分类
        List<CompletableFuture<FileClassificationResult>> futures = fileIds.stream()
                .map(fileId -> CompletableFuture.supplyAsync(() -> {
                    FileClassificationResult result = classifyFile(fileId);
                    if (result != null) {
                        result.setFileId(fileId);
                    }
                    return result;
                }))
                .toList();

        // 收集结果
        futures.forEach(future -> {
            try {
                FileClassificationResult result = future.get();
                if (result != null) {
                    results.add(result);
                }
            } catch (Exception e) {
                log.error("Failed to get batch classification result: {}", e.getMessage());
            }
        });

        return results;
    }

    /**
     * 获取预定义分类列表
     *
     * @return 分类列表
     */
    public List<String> getPredefinedCategories() {
        return PREDEFINED_CATEGORIES;
    }

    /**
     * 解析分类结果
     */
    private FileClassificationResult parseClassificationResult(String json) {
        FileClassificationResult result = new FileClassificationResult();

        try {
            // 清理可能的markdown代码块标记
            json = cleanJsonResponse(json);

            JsonNode root = objectMapper.readTree(json);

            if (root.has("category")) {
                result.setCategory(root.get("category").asText());
            }

            if (root.has("tags") && root.get("tags").isArray()) {
                List<String> tags = new ArrayList<>();
                root.get("tags").forEach(node -> tags.add(node.asText()));
                result.setTags(tags);
            }

            if (root.has("confidence")) {
                result.setConfidence(root.get("confidence").asDouble());
            }

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse classification result JSON: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 解析标签建议
     */
    private List<String> parseTagSuggestions(String json) {
        List<String> tags = new ArrayList<>();

        try {
            // 清理可能的markdown代码块标记
            json = cleanJsonResponse(json);

            JsonNode root = objectMapper.readTree(json);

            if (root.has("tags") && root.get("tags").isArray()) {
                root.get("tags").forEach(node -> tags.add(node.asText()));
            }

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse tag suggestions JSON: {}", e.getMessage());
        }

        return tags;
    }

    /**
     * 清理JSON响应中的markdown标记
     */
    private String cleanJsonResponse(String json) {
        if (json == null) {
            return "{}";
        }

        json = json.trim();
        if (json.startsWith("```json")) {
            json = json.substring(7);
        }
        if (json.startsWith("```")) {
            json = json.substring(3);
        }
        if (json.endsWith("```")) {
            json = json.substring(0, json.length() - 3);
        }
        return json.trim();
    }

    /**
     * 分类结果
     */
    @Data
    public static class FileClassificationResult {
        private String fileId;
        private String category;
        private List<String> tags;
        private Double confidence;
    }
}

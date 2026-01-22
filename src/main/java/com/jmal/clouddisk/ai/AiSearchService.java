package com.jmal.clouddisk.ai;

import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileIntroVO;
import com.jmal.clouddisk.model.query.SearchDTO;
import com.jmal.clouddisk.service.impl.CommonFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * AI搜索服务
 * 实现自然语言查询转换和混合搜索
 *
 * @author jmal
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jmalcloud.ai", name = "enabled", havingValue = "true")
public class AiSearchService {

    private final ChatClient chatClient;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final IFileDAO fileDAO;
    private final CommonFileService commonFileService;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    private static final String SEARCH_PROMPT_TEMPLATE = """
            你是一个文件搜索助手。用户会用自然语言描述他们要找的文件。
            请分析用户的意图，提取关键信息：
            - 文件类型（如：图片image、文档document、视频video、音频audio等）
            - 时间范围（如：最近一周、上个月等，转换为具体日期）
            - 关键词
            - 其他条件
            
            今天的日期是：%s
            
            用户查询：%s
            
            请返回JSON格式（只返回JSON，不要其他内容）：
            {
              "fileTypes": ["image", "document", "video", "audio"],
              "keywords": ["关键词1", "关键词2"],
              "timeRange": {"start": "2024-01-01", "end": "2024-12-31"},
              "sortBy": "updateDate",
              "sortOrder": "desc"
            }
            
            注意：
            1. fileTypes可以为空数组，表示搜索所有类型
            2. keywords为必要的关键词列表，用于搜索
            3. timeRange可以为null，表示不限时间
            4. sortBy可选值：updateDate, uploadDate, name, size
            5. sortOrder可选值：asc, desc
            """;

    /**
     * 自然语言搜索
     * 将用户的自然语言查询转换为结构化搜索条件
     *
     * @param query  用户查询
     * @param userId 用户ID
     * @return 搜索结果
     */
    public List<FileIntroVO> naturalLanguageSearch(String query, String userId) {
        if (CharSequenceUtil.isBlank(query)) {
            return new ArrayList<>();
        }

        try {
            // 解析自然语言查询
            SearchCondition condition = parseNaturalLanguageQuery(query);
            log.debug("Parsed search condition: {}", condition);

            // 构建搜索DTO
            SearchDTO searchDTO = buildSearchDTO(condition, userId);

            // 执行搜索
            List<String> fileIds = executeSearch(searchDTO, condition);

            // 获取文件详情
            return getFileDetails(fileIds, userId);
        } catch (Exception e) {
            log.error("Natural language search failed: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 语义向量搜索
     *
     * @param query  查询文本
     * @param userId 用户ID
     * @param topK   返回数量
     * @return 搜索结果
     */
    public List<FileIntroVO> semanticSearch(String query, String userId, int topK) {
        if (CharSequenceUtil.isBlank(query)) {
            return new ArrayList<>();
        }

        try {
            // 生成查询向量
            float[] queryVector = embeddingService.embed(query);

            if (queryVector.length == 0) {
                log.warn("Failed to generate query vector");
                return new ArrayList<>();
            }

            // 向量搜索
            List<VectorStoreService.VectorSearchResult> results = vectorStoreService.searchSimilar(queryVector, topK);

            // 获取文件详情
            List<String> fileIds = results.stream()
                    .map(VectorStoreService.VectorSearchResult::fileId)
                    .toList();

            return getFileDetails(fileIds, userId);
        } catch (Exception e) {
            log.error("Semantic search failed: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 混合搜索（关键词 + 向量）
     *
     * @param query  查询文本
     * @param userId 用户ID
     * @return 搜索结果
     */
    public List<FileIntroVO> hybridSearch(String query, String userId) {
        if (CharSequenceUtil.isBlank(query)) {
            return new ArrayList<>();
        }

        try {
            Set<String> allFileIds = new HashSet<>();

            // 1. 自然语言搜索
            List<FileIntroVO> nlResults = naturalLanguageSearch(query, userId);
            nlResults.forEach(f -> allFileIds.add(f.getId()));

            // 2. 语义搜索
            if (Boolean.TRUE.equals(aiProperties.getVectorSearchEnabled())) {
                List<FileIntroVO> semanticResults = semanticSearch(query, userId, aiProperties.getSearchTopK());
                semanticResults.forEach(f -> allFileIds.add(f.getId()));
            }

            // 获取去重后的文件详情
            return getFileDetails(new ArrayList<>(allFileIds), userId);
        } catch (Exception e) {
            log.error("Hybrid search failed: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 解析自然语言查询
     */
    private SearchCondition parseNaturalLanguageQuery(String query) {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            String prompt = String.format(SEARCH_PROMPT_TEMPLATE, today, query);

            String response = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();

            return parseSearchConditionFromJson(response);
        } catch (Exception e) {
            log.warn("Failed to parse natural language query, using fallback: {}", e.getMessage());
            // 降级处理：直接使用查询作为关键词
            SearchCondition condition = new SearchCondition();
            condition.setKeywords(List.of(query.split("\\s+")));
            return condition;
        }
    }

    /**
     * 从JSON解析搜索条件
     */
    private SearchCondition parseSearchConditionFromJson(String json) {
        SearchCondition condition = new SearchCondition();

        try {
            // 清理可能的markdown代码块标记
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
            json = json.trim();

            JsonNode root = objectMapper.readTree(json);

            // 解析fileTypes
            if (root.has("fileTypes") && root.get("fileTypes").isArray()) {
                List<String> fileTypes = new ArrayList<>();
                root.get("fileTypes").forEach(node -> fileTypes.add(node.asText()));
                condition.setFileTypes(fileTypes);
            }

            // 解析keywords
            if (root.has("keywords") && root.get("keywords").isArray()) {
                List<String> keywords = new ArrayList<>();
                root.get("keywords").forEach(node -> keywords.add(node.asText()));
                condition.setKeywords(keywords);
            }

            // 解析timeRange
            if (root.has("timeRange") && !root.get("timeRange").isNull()) {
                JsonNode timeRange = root.get("timeRange");
                if (timeRange.has("start")) {
                    condition.setTimeStart(timeRange.get("start").asText());
                }
                if (timeRange.has("end")) {
                    condition.setTimeEnd(timeRange.get("end").asText());
                }
            }

            // 解析排序
            if (root.has("sortBy")) {
                condition.setSortBy(root.get("sortBy").asText());
            }
            if (root.has("sortOrder")) {
                condition.setSortOrder(root.get("sortOrder").asText());
            }

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse search condition JSON: {}", e.getMessage());
        }

        return condition;
    }

    /**
     * 构建搜索DTO
     */
    private SearchDTO buildSearchDTO(SearchCondition condition, String userId) {
        SearchDTO.SearchDTOBuilder builder = SearchDTO.builder();
        builder.userId(userId);
        builder.includeFileName(true);
        builder.includeFileContent(true);
        builder.includeTagName(true);

        // 设置关键词
        if (condition.getKeywords() != null && !condition.getKeywords().isEmpty()) {
            builder.keyword(String.join(" ", condition.getKeywords()));
        }

        // 设置排序
        if (condition.getSortBy() != null) {
            builder.sortProp(condition.getSortBy());
        }
        if (condition.getSortOrder() != null) {
            builder.sortOrder(condition.getSortOrder());
        }

        builder.page(1);
        builder.pageSize(aiProperties.getSearchTopK());

        return builder.build();
    }

    /**
     * 执行搜索
     */
    private List<String> executeSearch(SearchDTO searchDTO, SearchCondition condition) {
        // 使用现有的文件搜索服务进行关键词搜索
        List<String> fileIds = new ArrayList<>();

        // 从数据库搜索
        String keyword = searchDTO.getKeyword();
        if (CharSequenceUtil.isNotBlank(keyword)) {
            // 使用DAO进行简单的关键词搜索
            List<String> results = fileDAO.searchFileIdsByKeyword(keyword, searchDTO.getUserId(), aiProperties.getSearchTopK());
            fileIds.addAll(results);
        }

        return fileIds;
    }

    /**
     * 获取文件详情
     */
    private List<FileIntroVO> getFileDetails(List<String> fileIds, String userId) {
        if (fileIds == null || fileIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<FileIntroVO> results = new ArrayList<>();
        for (String fileId : fileIds) {
            try {
                FileDocument fileDocument = commonFileService.getById(fileId);
                if (fileDocument != null) {
                    FileIntroVO file = convertToFileIntroVO(fileDocument);
                    results.add(file);
                }
            } catch (Exception e) {
                log.debug("Failed to get file details for {}: {}", fileId, e.getMessage());
            }
        }

        return results;
    }

    /**
     * 将FileDocument转换为FileIntroVO
     */
    private FileIntroVO convertToFileIntroVO(FileDocument fileDocument) {
        FileIntroVO vo = new FileIntroVO();
        vo.setId(fileDocument.getId());
        vo.setName(fileDocument.getName());
        vo.setPath(fileDocument.getPath());
        vo.setUserId(fileDocument.getUserId());
        vo.setSize(fileDocument.getSize());
        vo.setContentType(fileDocument.getContentType());
        vo.setIsFolder(fileDocument.getIsFolder());
        vo.setIsFavorite(fileDocument.getIsFavorite());
        vo.setTags(fileDocument.getTags());
        vo.setUpdateDate(fileDocument.getUpdateDate());
        vo.setUploadDate(fileDocument.getUploadDate());
        return vo;
    }

    /**
     * 搜索条件内部类
     */
    @lombok.Data
    public static class SearchCondition {
        private List<String> fileTypes;
        private List<String> keywords;
        private String timeStart;
        private String timeEnd;
        private String sortBy;
        private String sortOrder;
    }
}

package com.jmal.clouddisk.ai;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.service.impl.CommonFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 文件摘要服务
 * 自动生成文件内容摘要
 *
 * @author jmal
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "jmalcloud.ai", name = "enabled", havingValue = "true")
public class FileSummaryService {

    private final ChatClient chatClient;
    private final IFileDAO fileDAO;
    private final CommonFileService commonFileService;
    private final AiProperties aiProperties;
    private final ExecutorService aiExecutor;

    public FileSummaryService(ChatClient chatClient, IFileDAO fileDAO, 
                              CommonFileService commonFileService, AiProperties aiProperties) {
        this.chatClient = chatClient;
        this.fileDAO = fileDAO;
        this.commonFileService = commonFileService;
        this.aiProperties = aiProperties;
        this.aiExecutor = Executors.newFixedThreadPool(4);
    }

    private static final String SUMMARY_PROMPT_TEMPLATE = """
            请对以下文件内容生成简洁的摘要（不超过%d字）：
            
            文件名：%s
            文件类型：%s
            内容：
            %s
            
            请用中文回复，摘要应该包含文件的主要内容和关键信息。
            只返回摘要内容，不要包含任何前缀或解释。
            """;

    /**
     * 生成文件摘要
     *
     * @param fileId 文件ID
     * @return 文件摘要
     */
    public String generateSummary(String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            return null;
        }

        if (!Boolean.TRUE.equals(aiProperties.getSummaryEnabled())) {
            log.debug("Summary generation is disabled");
            return null;
        }

        try {
            // 获取文件信息
            FileDocument file = commonFileService.getById(fileId);
            if (file == null) {
                log.warn("File not found: {}", fileId);
                return null;
            }

            // 获取文件内容
            String content = getFileContent(fileId);
            if (CharSequenceUtil.isBlank(content)) {
                log.debug("No content to summarize for file: {}", fileId);
                return null;
            }

            // 限制内容长度以避免超出token限制
            String truncatedContent = truncateContent(content, 4000);

            // 生成摘要
            String prompt = String.format(SUMMARY_PROMPT_TEMPLATE,
                    aiProperties.getSummaryMaxLength(),
                    file.getName(),
                    file.getContentType() != null ? file.getContentType() : "未知",
                    truncatedContent);

            String summary = chatClient.prompt(new Prompt(prompt))
                    .call()
                    .content();

            // 存储摘要到数据库
            if (CharSequenceUtil.isNotBlank(summary)) {
                saveSummary(fileId, summary);
            }

            return summary;
        } catch (Exception e) {
            log.error("Failed to generate summary for file {}: {}", fileId, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取已有摘要
     *
     * @param fileId 文件ID
     * @return 文件摘要
     */
    public String getSummary(String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            return null;
        }

        try {
            return fileDAO.getFileSummary(fileId);
        } catch (Exception e) {
            log.debug("Failed to get summary for file {}: {}", fileId, e.getMessage());
            return null;
        }
    }

    /**
     * 重新生成摘要
     *
     * @param fileId 文件ID
     * @return 新摘要
     */
    public String regenerateSummary(String fileId) {
        // 清除旧摘要
        try {
            fileDAO.updateFileSummary(fileId, null);
        } catch (Exception e) {
            log.debug("Failed to clear old summary: {}", e.getMessage());
        }

        // 生成新摘要
        return generateSummary(fileId);
    }

    /**
     * 批量生成摘要
     *
     * @param fileIds 文件ID列表
     * @return 摘要列表
     */
    public List<FileSummaryResult> batchGenerateSummary(List<String> fileIds) {
        List<FileSummaryResult> results = new ArrayList<>();

        if (fileIds == null || fileIds.isEmpty()) {
            return results;
        }

        // 异步批量生成 - 使用自定义线程池
        List<CompletableFuture<FileSummaryResult>> futures = fileIds.stream()
                .map(fileId -> CompletableFuture.supplyAsync(() -> {
                    String summary = generateSummary(fileId);
                    return new FileSummaryResult(fileId, summary, summary != null);
                }, aiExecutor))
                .toList();

        // 收集结果
        futures.forEach(future -> {
            try {
                results.add(future.get());
            } catch (Exception e) {
                log.error("Failed to get batch summary result: {}", e.getMessage());
            }
        });

        return results;
    }

    /**
     * 获取文件内容
     */
    private String getFileContent(String fileId) {
        try {
            FileDocument file = commonFileService.getById(fileId);
            if (file != null && file.getContentText() != null) {
                return file.getContentText();
            }
        } catch (Exception e) {
            log.debug("Failed to get file content: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 截断内容
     */
    private String truncateContent(String content, int maxLength) {
        if (content == null) {
            return "";
        }
        if (content.length() <= maxLength) {
            return content;
        }
        return content.substring(0, maxLength) + "...";
    }

    /**
     * 保存摘要到数据库
     */
    private void saveSummary(String fileId, String summary) {
        try {
            fileDAO.updateFileSummary(fileId, summary);
        } catch (Exception e) {
            log.error("Failed to save summary for file {}: {}", fileId, e.getMessage());
        }
    }

    /**
     * 摘要结果内部类
     */
    public record FileSummaryResult(String fileId, String summary, boolean success) {
    }
}

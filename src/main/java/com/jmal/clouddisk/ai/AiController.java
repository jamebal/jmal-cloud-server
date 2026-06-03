package com.jmal.clouddisk.ai;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileIntroVO;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI功能控制器
 *
 * @author jmal
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI智能功能")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "jmalcloud.ai", name = "enabled", havingValue = "true")
public class AiController {

    private final AiSearchService aiSearchService;
    private final FileSummaryService fileSummaryService;
    private final FileClassificationService fileClassificationService;
    private final CommonFileService commonFileService;
    private final UserLoginHolder userLoginHolder;
    private final AiProperties aiProperties;

    @Operation(summary = "自然语言搜索")
    @GetMapping("/search")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<List<FileIntroVO>> naturalLanguageSearch(
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer pageSize) {

        if (CharSequenceUtil.isBlank(query)) {
            return ResultUtil.warning("查询内容不能为空");
        }

        String userId = userLoginHolder.getUserId();
        List<FileIntroVO> results = aiSearchService.naturalLanguageSearch(query, userId);
        return ResultUtil.success(results);
    }

    @Operation(summary = "语义搜索")
    @GetMapping("/semantic-search")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<List<FileIntroVO>> semanticSearch(
            @RequestParam String query,
            @RequestParam(defaultValue = "10") Integer topK) {

        if (CharSequenceUtil.isBlank(query)) {
            return ResultUtil.warning("查询内容不能为空");
        }

        if (!Boolean.TRUE.equals(aiProperties.getVectorSearchEnabled())) {
            return ResultUtil.warning("语义搜索功能未启用");
        }

        String userId = userLoginHolder.getUserId();
        List<FileIntroVO> results = aiSearchService.semanticSearch(query, userId, topK);
        return ResultUtil.success(results);
    }

    @Operation(summary = "混合搜索")
    @GetMapping("/hybrid-search")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<List<FileIntroVO>> hybridSearch(@RequestParam String query) {
        if (CharSequenceUtil.isBlank(query)) {
            return ResultUtil.warning("查询内容不能为空");
        }

        String userId = userLoginHolder.getUserId();
        List<FileIntroVO> results = aiSearchService.hybridSearch(query, userId);
        return ResultUtil.success(results);
    }

    @Operation(summary = "获取文件摘要")
    @GetMapping("/summary/{fileId}")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<FileSummaryVO> getFileSummary(@PathVariable String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            return ResultUtil.warning("文件ID不能为空");
        }

        FileSummaryVO vo = new FileSummaryVO();
        vo.setFileId(fileId);

        // 获取文件名
        FileDocument file = commonFileService.getById(fileId);
        if (file != null) {
            vo.setFileName(file.getName());
        }

        // 获取摘要
        String summary = fileSummaryService.getSummary(fileId);
        vo.setSummary(summary);
        vo.setHasSummary(CharSequenceUtil.isNotBlank(summary));

        return ResultUtil.success(vo);
    }

    @Operation(summary = "生成文件摘要")
    @PostMapping("/summary/{fileId}")
    @Permission("cloud:file:update")
    @LogOperatingFun
    public ResponseResult<FileSummaryVO> generateFileSummary(@PathVariable String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            return ResultUtil.warning("文件ID不能为空");
        }

        if (!Boolean.TRUE.equals(aiProperties.getSummaryEnabled())) {
            return ResultUtil.warning("摘要功能未启用");
        }

        FileSummaryVO vo = new FileSummaryVO();
        vo.setFileId(fileId);

        // 获取文件名
        FileDocument file = commonFileService.getById(fileId);
        if (file != null) {
            vo.setFileName(file.getName());
        }

        // 生成摘要
        String summary = fileSummaryService.generateSummary(fileId);
        vo.setSummary(summary);
        vo.setHasSummary(CharSequenceUtil.isNotBlank(summary));
        vo.setGenerateTime(System.currentTimeMillis());

        if (CharSequenceUtil.isBlank(summary)) {
            return ResultUtil.warning("摘要生成失败，请检查文件内容");
        }

        return ResultUtil.success(vo);
    }

    @Operation(summary = "重新生成文件摘要")
    @PostMapping("/summary/{fileId}/regenerate")
    @Permission("cloud:file:update")
    @LogOperatingFun
    public ResponseResult<FileSummaryVO> regenerateFileSummary(@PathVariable String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            return ResultUtil.warning("文件ID不能为空");
        }

        if (!Boolean.TRUE.equals(aiProperties.getSummaryEnabled())) {
            return ResultUtil.warning("摘要功能未启用");
        }

        FileSummaryVO vo = new FileSummaryVO();
        vo.setFileId(fileId);

        // 获取文件名
        FileDocument file = commonFileService.getById(fileId);
        if (file != null) {
            vo.setFileName(file.getName());
        }

        // 重新生成摘要
        String summary = fileSummaryService.regenerateSummary(fileId);
        vo.setSummary(summary);
        vo.setHasSummary(CharSequenceUtil.isNotBlank(summary));
        vo.setGenerateTime(System.currentTimeMillis());

        return ResultUtil.success(vo);
    }

    @Operation(summary = "获取文件分类")
    @GetMapping("/classify/{fileId}")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<FileClassificationVO> classifyFile(@PathVariable String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            return ResultUtil.warning("文件ID不能为空");
        }

        if (!Boolean.TRUE.equals(aiProperties.getClassificationEnabled())) {
            return ResultUtil.warning("分类功能未启用");
        }

        FileClassificationService.FileClassificationResult result = fileClassificationService.classifyFile(fileId);

        FileClassificationVO vo = new FileClassificationVO();
        vo.setFileId(fileId);

        // 获取文件名
        FileDocument file = commonFileService.getById(fileId);
        if (file != null) {
            vo.setFileName(file.getName());
        }

        if (result != null) {
            vo.setCategory(result.getCategory());
            vo.setTags(result.getTags());
            vo.setConfidence(result.getConfidence());
        }

        return ResultUtil.success(vo);
    }

    @Operation(summary = "获取标签建议")
    @GetMapping("/suggest-tags/{fileId}")
    @Permission("cloud:file:list")
    @LogOperatingFun(logType = LogOperation.Type.BROWSE)
    public ResponseResult<TagSuggestionVO> suggestTags(@PathVariable String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            return ResultUtil.warning("文件ID不能为空");
        }

        List<String> tags = fileClassificationService.suggestTags(fileId);

        TagSuggestionVO vo = new TagSuggestionVO();
        vo.setFileId(fileId);

        // 获取文件名
        FileDocument file = commonFileService.getById(fileId);
        if (file != null) {
            vo.setFileName(file.getName());
        }

        vo.setSuggestedTags(tags);

        return ResultUtil.success(vo);
    }

    @Operation(summary = "自动打标签")
    @PostMapping("/auto-tag/{fileId}")
    @Permission("cloud:file:update")
    @LogOperatingFun
    public ResponseResult<Object> autoTagFile(@PathVariable String fileId) {
        if (CharSequenceUtil.isBlank(fileId)) {
            return ResultUtil.warning("文件ID不能为空");
        }

        String userId = userLoginHolder.getUserId();
        boolean success = fileClassificationService.autoTagFile(fileId, userId);

        if (success) {
            return ResultUtil.success();
        } else {
            return ResultUtil.warning("自动打标签失败");
        }
    }

    @Operation(summary = "批量分类")
    @PostMapping("/batch-classify")
    @Permission("cloud:file:update")
    @LogOperatingFun
    public ResponseResult<List<FileClassificationVO>> batchClassify(@RequestBody List<String> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return ResultUtil.warning("文件ID列表不能为空");
        }

        if (!Boolean.TRUE.equals(aiProperties.getClassificationEnabled())) {
            return ResultUtil.warning("分类功能未启用");
        }

        List<FileClassificationService.FileClassificationResult> results = fileClassificationService.batchClassify(fileIds);

        List<FileClassificationVO> voList = results.stream()
                .map(result -> {
                    FileClassificationVO vo = new FileClassificationVO();
                    vo.setFileId(result.getFileId());
                    vo.setCategory(result.getCategory());
                    vo.setTags(result.getTags());
                    vo.setConfidence(result.getConfidence());
                    return vo;
                })
                .toList();

        return ResultUtil.success(voList);
    }

    @Operation(summary = "获取预定义分类列表")
    @GetMapping("/categories")
    @Permission("cloud:file:list")
    public ResponseResult<List<String>> getPredefinedCategories() {
        return ResultUtil.success(fileClassificationService.getPredefinedCategories());
    }

    @Operation(summary = "检查AI功能状态")
    @GetMapping("/status")
    public ResponseResult<AiStatusVO> getAiStatus() {
        AiStatusVO status = new AiStatusVO();
        status.setEnabled(Boolean.TRUE.equals(aiProperties.getEnabled()));
        status.setProvider(aiProperties.getProvider());
        status.setVectorSearchEnabled(Boolean.TRUE.equals(aiProperties.getVectorSearchEnabled()));
        status.setSummaryEnabled(Boolean.TRUE.equals(aiProperties.getSummaryEnabled()));
        status.setClassificationEnabled(Boolean.TRUE.equals(aiProperties.getClassificationEnabled()));
        return ResultUtil.success(status);
    }

    /**
     * AI状态VO
     */
    @lombok.Data
    public static class AiStatusVO {
        private Boolean enabled;
        private String provider;
        private Boolean vectorSearchEnabled;
        private Boolean summaryEnabled;
        private Boolean classificationEnabled;
    }
}

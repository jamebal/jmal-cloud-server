package com.jmal.clouddisk.ai;

import lombok.Data;

import java.util.List;

/**
 * 标签建议VO
 *
 * @author jmal
 */
@Data
public class TagSuggestionVO {
    /**
     * 文件ID
     */
    private String fileId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 建议的标签列表
     */
    private List<String> suggestedTags;
}

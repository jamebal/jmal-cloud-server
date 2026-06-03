package com.jmal.clouddisk.ai;

import lombok.Data;

/**
 * 文件摘要VO
 *
 * @author jmal
 */
@Data
public class FileSummaryVO {
    /**
     * 文件ID
     */
    private String fileId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 摘要内容
     */
    private String summary;

    /**
     * 是否已生成摘要
     */
    private Boolean hasSummary;

    /**
     * 生成时间
     */
    private Long generateTime;
}

package com.jmal.clouddisk.ai;

import lombok.Data;

import java.util.List;

/**
 * 文件分类VO
 *
 * @author jmal
 */
@Data
public class FileClassificationVO {
    /**
     * 文件ID
     */
    private String fileId;

    /**
     * 文件名
     */
    private String fileName;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签列表
     */
    private List<String> tags;

    /**
     * 置信度
     */
    private Double confidence;
}

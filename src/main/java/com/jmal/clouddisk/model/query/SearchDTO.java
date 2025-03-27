package com.jmal.clouddisk.model.query;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jmal
 * @Description Lucene search param
 * @Date 2021/4/27 5:22 下午
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Schema
public class SearchDTO extends QueryBaseDTO {
    String userId;
    /**
     * 要查询的关键字
     */
    String keyword;
    /**
     * 要查询的文件类型
     */
    String type;
    /**
     * 当前目录
     */
    String currentDirectory;
    /**
     * 是否是文件夹
     */
    Boolean isFolder;
    /**
     * 是否是收藏
     */
    Boolean isFavorite;
    /**
     * tagId
     */
    String tagId;
    /**
     * 查询的文件夹
     */
    String folder;

    /**
     * 修改时间范围开始
     */
    Long modifyStart;

    /**
     * 修改时间范围结束
     */
    Long modifyEnd;

    /**
     * 文件大小范围最小
     */
    Long sizeMin;

    /**
     * 文件大小范围最大
     */
    Long sizeMax;
}

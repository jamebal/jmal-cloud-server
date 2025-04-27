package com.jmal.clouddisk.model.query;

import cn.hutool.core.util.BooleanUtil;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * @author jmal
 * @Description Lucene search param
 * @Date 2021/4/27 5:22 下午
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@Schema
public class SearchDTO extends QueryBaseDTO {

    String id;

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

    /**
     * 是否搜索挂载盘
     */
    Boolean searchMount;

    /**
     * 是否全盘搜索(用户根目录)
     */
    Boolean searchOverall;

    public String getCurrentDirectory() {
        if (BooleanUtil.isTrue(searchOverall)) {
            return null;
        }
        return currentDirectory;
    }

    public SearchOptionHistoryDO toSearchOptionDO() {
        SearchOptionHistoryDO.SearchOptionHistoryDOBuilder builder = SearchOptionHistoryDO.builder();
        builder.id(id)
                .userId(userId)
                .keyword(keyword)
                .type(type)
                .currentDirectory(currentDirectory)
                .isFolder(isFolder)
                .isFavorite(isFavorite)
                .tagId(tagId)
                .folder(folder)
                .modifyStart(modifyStart)
                .modifyEnd(modifyEnd)
                .sizeMin(sizeMin)
                .sizeMax(sizeMax)
                .searchMount(searchMount)
                .searchOverall(searchOverall);
        return builder.build();
    }
}

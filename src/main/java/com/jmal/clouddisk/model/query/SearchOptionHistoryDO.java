package com.jmal.clouddisk.model.query;

import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * @author jmal
 * @Description Lucene search param
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@Document(collection = "searchOptionHistory")
public class SearchOptionHistoryDO extends QueryBaseDTO {

    String id;

    /**
     * 搜索时间
     */
    Long searchTime;
    /**
     * 执行搜索操作的用户id
     */
    String searchUserId;

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

    public SearchDTO toSearchDTO() {
        SearchDTO.SearchDTOBuilder builder = SearchDTO.builder();
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

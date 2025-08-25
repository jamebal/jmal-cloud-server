package com.jmal.clouddisk.model.query;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.config.Reflective;
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
public class SearchDTO extends QueryBaseDTO implements Reflective {

    String id;

    String userId;
    /**
     * 挂载盘用户id
     */
    String mountUserId;
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
     * 是否挂载
     */
    Boolean isMount;
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

    /**
     * 是否精确匹配
     */
    Boolean exactSearch;

    /**
     * 是否包含标签名称
     */
    Boolean includeTagName;

    /**
     * 是否包含文件名
     */
    Boolean includeFileName;

    /**
     * 是否包含文件内容
     */
    Boolean includeFileContent;

    public String getSearchUserId() {
        if (CharSequenceUtil.isEmpty(mountUserId)) {
            return userId;
        }
        return isSearchMountSubFolder() ? mountUserId : userId;
    }

    /**
     * 是否搜索挂载子目录
     */
    public boolean isSearchMountSubFolder() {
        if (BooleanUtil.isTrue(searchMount)) {
            return false;
        }
        String path = getCurrentDirectory();
        return CharSequenceUtil.isNotEmpty(folder) && !"/".equals(path);
    }

    /**
     * 是否只搜索所有挂载盘
     */
    public boolean onlySearchMount() {
        return BooleanUtil.isTrue(isMount) && !isSearchMountSubFolder();
    }

    public String getCurrentDirectory() {
        if (BooleanUtil.isTrue(searchOverall)) {
            return null;
        }
        return currentDirectory;
    }

    public String getTagId() {
        String path = getCurrentDirectory();
        if (CharSequenceUtil.isNotEmpty(path) && !"/".equals(path)) {
            return null;
        }
        return tagId;
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
                .searchOverall(searchOverall)
                .exactSearch(exactSearch)
                .includeTagName(includeTagName)
                .includeFileName(includeFileName)
                .includeFileContent(includeFileContent);
        return builder.build();
    }
}

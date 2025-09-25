package com.jmal.clouddisk.model.query;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * @author jmal
 * @Description Lucene search param
 */
@Getter
@Setter
@NoArgsConstructor
@Document(collection = "searchOptionHistory")
@Entity
@Table(name = "search_history",
        indexes = {
                @Index(name = "search_history_user_id", columnList = "userId"),
                @Index(name = "search_history_search_time", columnList = "searchTime"),
                @Index(name = "search_history_keyword", columnList = "keyword"),
        }
)
public class SearchOptionHistoryDO extends AuditableEntity implements Reflective {

    /**
     * 搜索时间
     */
    Long searchTime;

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
                .searchOverall(searchOverall)
                .exactSearch(exactSearch)
                .includeTagName(includeTagName)
                .includeFileName(includeFileName)
                .includeFileContent(includeFileContent);
        return builder.build();
    }
}

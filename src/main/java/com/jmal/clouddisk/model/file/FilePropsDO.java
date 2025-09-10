package com.jmal.clouddisk.model.file;

import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.model.Tag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.HashSet;
import java.util.Set;

/**
 * FileDocument 文件模型
 *
 * @author jmal
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity
@Table(name = "file_props")
public class FilePropsDO implements Reflective {

    @Id
    private String id;

    /**
     * 用于存储不同类型的二进制数据，如缩略图、文本内容, 使用文件存储, contentPath就是文件路径 ${rootDir}/${dbDir}/data/${fileId}/content/${fileId}
     */
    private Boolean hasContent;

    /**
     * ${rootDir}/${dbDir}/data/${fileId}/contentText/${fileId}
     */
    private Boolean hasContentText;

    /**
     * ${rootDir}/${dbDir}/data/${fileId}/html/${fileId}
     */
    private Boolean hasHtml;

    private Boolean shareBase;
    private Boolean subShare;
    private String shareId;
    private Integer LuceneIndex;

    @Column(name = "share_props")
    @JdbcTypeCode(SqlTypes.JSON)
    private ShareProperties shareProps;

    @Column(name = "props")
    @JdbcTypeCode(SqlTypes.JSON)
    private OtherProperties props;

    @Column(name = "tags")
    @JdbcTypeCode(SqlTypes.JSON)
    private Set<Tag> tags = new HashSet<>();

    private Integer delTag;

    public FilePropsDO(FileDocument fileDocument) {
        this.id = fileDocument.getId();
        cover(fileDocument);
    }

    public void updateFields(FileDocument fileDocument) {
        cover(fileDocument);
    }

    private void cover(FileDocument fileDocument) {
        this.shareBase = fileDocument.getShareBase();
        this.subShare = fileDocument.getSubShare();
        this.shareId = fileDocument.getShareId();

        this.shareProps = new ShareProperties(fileDocument);
        this.props = new OtherProperties(fileDocument);
        this.tags = new HashSet<>();
        if (fileDocument.getTags() != null) {
            this.tags.addAll(fileDocument.getTags());
        }
        this.delTag = fileDocument.getDelete();

        this.hasContent = fileDocument.getContent() != null;
        this.hasContentText = CharSequenceUtil.isNotBlank(fileDocument.getContentText());
        this.hasHtml = CharSequenceUtil.isNotBlank(fileDocument.getHtml());

        this.LuceneIndex = fileDocument.getIndex();
    }


    @Override
    public int hashCode() {
        int hash = 9;
        hash = 99 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }

}

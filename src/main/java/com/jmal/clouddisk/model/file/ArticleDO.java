package com.jmal.clouddisk.model.file;

import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity
@Table(name = "articles")
public class ArticleDO extends AuditableEntity implements Reflective {

    @Column(name = "is_release")
    private Boolean release;
    private Boolean alonePage;
    private Integer pageSort;
    /**
     * ${rootDir}/${dbDir}/data/${fileId}/draft/${fileId}
     */
    private Boolean hasDraft;
    private String cover;
    private String slug;

    @Column(name = "category_ids")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> categoryIds;

    @Column(name = "tag_ids")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<String> tagIds;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", // 在 articles 表中创建的外键列名
            referencedColumnName = "id", // file_id 列引用的是 files 表的 id 列
            unique = true, // 确保一个文件只能被一篇文章引用，强制一对一
            nullable = false, // 数据库层面强制外键不能为空
            foreignKey = @ForeignKey(name = "fk_article_to_file")) // DDL生成时外键约束的名称
    private FileMetadataDO fileMetadata;

    public ArticleDO(String id, Boolean alonePage, String slug, LocalDateTime updateDate) {
        this.id = id;
        this.alonePage = alonePage;
        this.slug = slug;
        this.fileMetadata = new FileMetadataDO();
        this.fileMetadata.setUpdateDate(updateDate);
    }

    public ArticleDO(FileDocument fileDocument) {
        this.id = fileDocument.getId();
        this.release = fileDocument.getRelease();
        this.alonePage = fileDocument.getAlonePage();
        this.pageSort = fileDocument.getPageSort();

        this.hasDraft = CharSequenceUtil.isNotBlank(fileDocument.getDraft());

        this.cover = fileDocument.getCover();
        this.slug = fileDocument.getSlug();
        this.categoryIds = fileDocument.getCategoryIds() != null ? List.of(fileDocument.getCategoryIds()) : null;
        this.tagIds = fileDocument.getTagIds() != null ? List.of(fileDocument.getTagIds()) : null;
    }

    public FileDocument toFileDocument() {
        FileDocument fileDocument;
        if (this.fileMetadata != null) {
            fileDocument = this.fileMetadata.toFileDocument();
        } else {
            fileDocument = new FileDocument();
            fileDocument.setId(this.id);
        }
        fileDocument.setRelease(this.release);
        fileDocument.setAlonePage(this.alonePage);
        fileDocument.setPageSort(this.pageSort);
        fileDocument.setCover(this.cover);
        fileDocument.setSlug(this.slug);
        fileDocument.setCategoryIds(this.categoryIds != null ? this.categoryIds.toArray(new String[0]) : null);
        fileDocument.setTagIds(this.tagIds != null ? this.tagIds.toArray(new String[0]) : null);
        return fileDocument;
    }
}

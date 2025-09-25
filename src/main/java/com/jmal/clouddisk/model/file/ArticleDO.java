package com.jmal.clouddisk.model.file;

import cn.hutool.core.text.CharSequenceUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditablePerformanceEntity;
import com.jmal.clouddisk.model.ArticleVO;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bson.types.ObjectId;
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
@Table(name = "articles",
        indexes = {
                @Index(name = "articles_slug", columnList = "slug"),
        }
)
public class ArticleDO extends AuditablePerformanceEntity implements Reflective {

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

    @OneToOne(fetch = FetchType.LAZY, optional = false, cascade = {CascadeType.PERSIST, CascadeType.MERGE, CascadeType.REMOVE})
    @JoinColumn(name = "file_id", // 在 articles 表中创建的外键列名
            referencedColumnName = "id", // file_id 列引用的是 files 表的 id 列
            unique = true, // 确保一个文件只能被一篇文章引用，强制一对一
            nullable = false, // 数据库层面强制外键不能为空
            foreignKey = @ForeignKey(name = "fk_article_to_file")) // DDL生成时外键约束的名称
    private FileMetadataDO fileMetadata;

    public ArticleDO(String id, Boolean alonePage, String slug, LocalDateTime updateDate) {
        setId(id);
        this.alonePage = alonePage;
        this.slug = slug;
        this.fileMetadata = new FileMetadataDO();
        this.fileMetadata.setUpdateDate(updateDate);
    }

    public ArticleDO(FileDocument fileDocument) {
        if (fileDocument.getId() == null) {
            fileDocument.setId(new ObjectId().toHexString());
        }
        setId(fileDocument.getId());
        this.cover(fileDocument);
    }

    public void updateFields(FileDocument fileDocument) {
        cover(fileDocument);
        this.fileMetadata.updateFields(fileDocument);
    }

    private void cover(FileDocument fileDocument) {
        this.release = fileDocument.getRelease();
        this.alonePage = fileDocument.getAlonePage();
        this.pageSort = fileDocument.getPageSort();
        this.hasDraft = CharSequenceUtil.isNotBlank(fileDocument.getDraft());
        this.cover = fileDocument.getCover();
        this.slug = fileDocument.getSlug();
        this.categoryIds = fileDocument.getCategoryIds();
        this.tagIds = fileDocument.getTagIds();
    }

    public FileDocument toFileDocument() {
        FileDocument fileDocument;
        if (this.fileMetadata != null) {
            fileDocument = this.fileMetadata.toFileDocument();
        } else {
            fileDocument = new FileDocument();
            fileDocument.setId(this.getId());
        }
        fileDocument.setRelease(this.release);
        fileDocument.setAlonePage(this.alonePage);
        fileDocument.setPageSort(this.pageSort);
        fileDocument.setCover(this.cover);
        fileDocument.setSlug(this.slug);
        fileDocument.setCategoryIds(this.categoryIds);
        fileDocument.setTagIds(this.tagIds);
        return fileDocument;
    }

    public ArticleVO toArticleVO() {
        ArticleVO articleVO = new ArticleVO();
        articleVO.setRelease(this.release);
        articleVO.setCover(this.cover);
        articleVO.setSlug(this.slug);
        articleVO.setUserId(this.fileMetadata.getUserId());
        articleVO.setCategoryIds(this.categoryIds);
        articleVO.setTagIds(this.tagIds);
        articleVO.setSuffix(this.fileMetadata.getSuffix());
        articleVO.setAlonePage(this.alonePage);
        articleVO.setName(this.fileMetadata.getName());
        articleVO.setUpdateDate(this.fileMetadata.getUpdateDate());
        articleVO.setUploadDate(this.fileMetadata.getUploadDate());
        return articleVO;
    }
}

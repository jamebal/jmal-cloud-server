package com.jmal.clouddisk.model.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.model.Tag;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.nio.charset.StandardCharsets;
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

    private BlobType blobType;

    @Lob
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "blob_data")
    private byte[] blob;

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

        if (fileDocument.getContent() != null) {
            this.blobType = BlobType.thumbnail;
            this.blob = fileDocument.getContent();
        }
        if (fileDocument.getContentText() != null) {
            this.blobType = BlobType.contentText;
            this.blob = fileDocument.getContentText().getBytes(StandardCharsets.UTF_8);
        }
        if (fileDocument.getHtml() != null) {
            this.blobType = BlobType.html;
            this.blob = fileDocument.getHtml().getBytes(StandardCharsets.UTF_8);
        }
        this.LuceneIndex = fileDocument.getIndex();
    }


    @Override
    public int hashCode() {
        int hash = 9;
        hash = 99 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }

}

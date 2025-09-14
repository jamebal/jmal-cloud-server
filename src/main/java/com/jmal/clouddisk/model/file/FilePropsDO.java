package com.jmal.clouddisk.model.file;

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

import java.util.List;

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
    @Column(length = 24)
    private String id;

    private Boolean shareBase;
    private Boolean subShare;
    @Column(length = 24)
    private String shareId;

    @Column(name = "share_props")
    @JdbcTypeCode(SqlTypes.JSON)
    private ShareProperties shareProps;

    @Column(name = "props")
    @JdbcTypeCode(SqlTypes.JSON)
    private OtherProperties props;

    @Column(name = "tags")
    @JdbcTypeCode(SqlTypes.JSON)
    private List<Tag> tags;

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
        if (fileDocument.getTags() != null) {
            this.tags.addAll(fileDocument.getTags());
        }
    }

    public void toFileDocumentFragment(FileDocument fileDocument) {
        fileDocument.setIsPublic(this.shareProps.getIsPublic());
        fileDocument.setIsShare(this.shareProps.getIsShare());
        fileDocument.setIsPrivacy(this.shareProps.getIsPrivacy());
        fileDocument.setExtractionCode(this.shareProps.getExtractionCode());
        fileDocument.setExpiresAt(this.shareProps.getExpiresAt());
        fileDocument.setOperationPermissionList(this.shareProps.getOperationPermissionList());
        fileDocument.setOssPlatform(this.props.getOssPlatform());
        fileDocument.setMusic(this.props.getMusic());
        fileDocument.setExif(this.props.getExif());
        fileDocument.setVideo(this.props.getVideo());
        fileDocument.setW(this.props.getW());
        fileDocument.setH(this.props.getH());
        fileDocument.setMediaCover(this.props.getMediaCover());
        fileDocument.setM3u8(this.props.getM3u8());
        fileDocument.setVtt(this.props.getVtt());
        fileDocument.setShowCover(this.props.getShowCover());
        fileDocument.setRemark(this.props.getRemark());
    }

    public void toFileIntroVOFragment(FileIntroVO fileDocument) {
        fileDocument.setIsShare(this.shareProps.getIsShare());
        fileDocument.setIsPrivacy(this.shareProps.getIsPrivacy());
        fileDocument.setExpiresAt(this.shareProps.getExpiresAt());
        fileDocument.setOperationPermissionList(this.shareProps.getOperationPermissionList());
        fileDocument.setOssPlatform(this.props.getOssPlatform());
        fileDocument.setMusic(this.props.getMusic());
        fileDocument.setExif(this.props.getExif());
        fileDocument.setVideo(this.props.getVideo());
        fileDocument.setW(this.props.getW());
        fileDocument.setH(this.props.getH());
        fileDocument.setMediaCover(this.props.getMediaCover());
        fileDocument.setM3u8(this.props.getM3u8());
        fileDocument.setShowCover(this.props.getShowCover());
        fileDocument.setRemark(this.props.getRemark());
    }


    @Override
    public int hashCode() {
        int hash = 9;
        hash = 99 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }

}

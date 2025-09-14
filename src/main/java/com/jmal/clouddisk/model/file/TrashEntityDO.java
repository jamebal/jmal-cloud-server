package com.jmal.clouddisk.model.file;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import com.jmal.clouddisk.model.Trash;
import com.jmal.clouddisk.service.impl.CommonFileService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * FileDocument 文件模型
 *
 * @author jmal
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = CommonFileService.TRASH_COLLECTION_NAME)
public class TrashEntityDO extends AuditableEntity implements Reflective {

    @Column(length = 24)
    private String userId;
    private Boolean isFolder;
    private String name;
    @Column(length = 64)
    private String md5;
    private String path;
    private Long size;
    @Column(length = 128)
    private String contentType;
    @Column(length = 32)
    private String suffix;
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadDate;
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateDate;

    @Column(name = "props")
    @JdbcTypeCode(SqlTypes.JSON)
    private OtherProperties props;

    private Boolean hasContent;

    /**
     * 是否隐藏显示
     */
    private Boolean hidden;
    /**
     * 是否移动到回收站, 从原位置移动到jmalcloudTrashDir
     */
    private Boolean move;

    @Override
    public int hashCode() {
        int hash = 17;
        hash = 89 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }

    public TrashEntityDO(Trash trash) {
        this.id = trash.getId();
        this.userId = trash.getUserId();
        this.isFolder = trash.getIsFolder();
        this.name = trash.getName();
        this.md5 = trash.getMd5();
        this.path = trash.getPath();
        this.size = trash.getSize();
        this.contentType = trash.getContentType();
        this.suffix = trash.getSuffix();
        this.uploadDate = trash.getUploadDate();
        this.updateDate = trash.getUpdateDate();
        this.hidden = trash.getHidden();
        this.move = trash.getMove();
        this.hasContent = trash.getContent() != null && trash.getContent().length > 0;
        OtherProperties otherProperties = new OtherProperties();
        otherProperties.setW(trash.getW());
        otherProperties.setH(trash.getH());
        otherProperties.setMusic(trash.getMusic());
        otherProperties.setExif(trash.getExif());
        otherProperties.setVideo(trash.getVideo());
        otherProperties.setM3u8(trash.getM3u8());
        otherProperties.setVtt(trash.getVtt());
        this.props = otherProperties;
    }

    public FileDocument toFileDocument() {
        FileDocument fileDocument = new FileDocument();
        fileDocument.setId(this.getId());
        fileDocument.setUserId(this.getUserId());
        fileDocument.setIsFolder(this.getIsFolder());
        fileDocument.setName(this.getName());
        fileDocument.setMd5(this.getMd5());
        fileDocument.setPath(this.getPath());
        fileDocument.setSize(this.getSize());
        fileDocument.setContentType(this.getContentType());
        fileDocument.setSuffix(this.getSuffix());
        fileDocument.setUploadDate(this.getUploadDate());
        fileDocument.setUpdateDate(this.getUpdateDate());
        if (this.getProps() != null) {
            fileDocument.setW(this.getProps().getW());
            fileDocument.setH(this.getProps().getH());
            fileDocument.setMusic(this.getProps().getMusic());
            fileDocument.setExif(this.getProps().getExif());
            fileDocument.setVideo(this.getProps().getVideo());
            fileDocument.setM3u8(this.getProps().getM3u8());
            fileDocument.setVtt(this.getProps().getVtt());
        }
        return fileDocument;
    }

    public FileIntroVO toFileIntroVO() {
        FileIntroVO fileIntroVO = new FileIntroVO();
        fileIntroVO.setId(this.getId());
        fileIntroVO.setUserId(this.getUserId());
        fileIntroVO.setIsFolder(this.getIsFolder());
        fileIntroVO.setName(this.getName());
        fileIntroVO.setMd5(this.getMd5());
        fileIntroVO.setPath(this.getPath());
        fileIntroVO.setSize(this.getSize());
        fileIntroVO.setContentType(this.getContentType());
        fileIntroVO.setSuffix(this.getSuffix());
        fileIntroVO.setUploadDate(this.getUploadDate());
        fileIntroVO.setUpdateDate(this.getUpdateDate());
        if (this.getProps() != null) {
            fileIntroVO.setW(this.getProps().getW());
            fileIntroVO.setH(this.getProps().getH());
            fileIntroVO.setMusic(this.getProps().getMusic());
            fileIntroVO.setExif(this.getProps().getExif());
            fileIntroVO.setVideo(this.getProps().getVideo());
            fileIntroVO.setM3u8(this.getProps().getM3u8());
        }
        return fileIntroVO;
    }

}

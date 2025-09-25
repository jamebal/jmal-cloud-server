package com.jmal.clouddisk.model.file;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import com.jmal.clouddisk.model.GridFSBO;
import com.jmal.clouddisk.model.Metadata;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

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
@Table(name = "file_history",
        indexes = {
                @Index(name = "file_history_links_file_id", columnList = "fileId"),
        }
)
public class FileHistoryDO extends AuditableEntity implements Reflective {

    String fileId;
    LocalDateTime uploadDate;
    String filepath;
    String filename;
    String time;
    String compression;
    String charset;
    String operator;
    Long size;

    public FileHistoryDO(GridFSBO gridFSBO) {
        this.id = gridFSBO.getId();
        this.fileId = gridFSBO.getFilename();
        this.uploadDate = gridFSBO.getUploadDate();
        if (gridFSBO.getMetadata() != null) {
            this.filepath = gridFSBO.getMetadata().getFilepath();
            this.filename = gridFSBO.getMetadata().getFilename();
            this.time = gridFSBO.getMetadata().getTime();
            this.compression = gridFSBO.getMetadata().getCompression();
            this.operator = gridFSBO.getMetadata().getOperator();
            this.size = gridFSBO.getMetadata().getSize();
        }
    }

    public FileHistoryDO(String fileId, Metadata metadata) {
        this.fileId = fileId;
        if (metadata != null) {
            this.filepath = metadata.getFilepath();
            this.filename = metadata.getFilename();
            this.time = metadata.getTime();
            this.compression = metadata.getCompression();
            this.operator = metadata.getOperator();
            this.size = metadata.getSize();
        }
        this.uploadDate = LocalDateTime.now();
    }

}

package com.jmal.clouddisk.model.file;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.service.Constants;
import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * FileDocument 文件模型
 *
 * @author jmal
 */
@Getter
@Setter
@NoArgsConstructor
public class FileHistoryDTO implements Reflective {

    String id;
    String fileId;
    String filename;
    String compression;
    String charset;
    Long size;


    public FileHistoryDTO(GridFSFile gridFSFile) {
        this.id = gridFSFile.getObjectId().toHexString();
        this.fileId = gridFSFile.getFilename();
        if (gridFSFile.getMetadata() != null) {
            this.compression = gridFSFile.getMetadata().getString("compression");
            this.charset = gridFSFile.getMetadata().getString("charset");
            this.filename = gridFSFile.getMetadata().getString(Constants.FILENAME);
            this.size = gridFSFile.getMetadata().getLong(Constants.SIZE);
        }
    }

    public FileHistoryDTO(String id, String fileId, String filename, String compression, String charset, Long size) {
        this.id = id;
        this.fileId = fileId;
        this.filename = filename;
        this.compression = compression;
        this.charset = charset;
        this.size = size;
    }
}

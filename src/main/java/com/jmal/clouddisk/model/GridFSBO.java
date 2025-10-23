package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jmal.clouddisk.config.Reflective;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author jmal
 * @Description GridFSDO
 * @date 2023/5/10 16:43
 */
@Data
@NoArgsConstructor
public class GridFSBO implements Reflective {

    String id;

    String filename;

    long length;

    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    LocalDateTime uploadDate;

    Metadata metadata;

    public GridFSBO(String id, String filename, long length, LocalDateTime uploadDate, String filepath, String name, String time, String operator, Long size) {
        this.id = id;
        this.filename = filename;
        this.length = length;
        this.uploadDate = uploadDate;
        Metadata metadata = new Metadata();
        metadata.setFilepath(filepath);
        metadata.setFilename(name);
        metadata.setTime(time);
        metadata.setOperator(operator);
        metadata.setSize(size);
        this.metadata = metadata;

    }

}

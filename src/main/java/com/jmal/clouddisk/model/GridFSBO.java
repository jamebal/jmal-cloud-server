package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jmal.clouddisk.config.Reflective;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author jmal
 * @Description GridFSDO
 * @date 2023/5/10 16:43
 */
@Data
public class GridFSBO implements Reflective {

    String id;

    String filename;

    long length;

    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    LocalDateTime uploadDate;

    Metadata metadata;

}

package com.jmal.clouddisk.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BurnNoteVO {

    private String id;

    private String userId;

    private Boolean isFile;

    private Long fileSize;

    private Integer viewsLeft;

    private Instant expireAt;

    private Instant createdTime;

}

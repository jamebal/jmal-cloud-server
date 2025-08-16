package com.jmal.clouddisk.model;

import com.alibaba.fastjson.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.service.impl.DirectLinkService;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
@Document(collection = DirectLinkService.COLLECTION_NAME)
@CompoundIndexes({
        @CompoundIndex(name = "mark_1", def = "{'mark': 1}"),
        @CompoundIndex(name = "fileId_1", def = "{'fileId': 1}"),

})
public class DirectLink implements Reflective {
    String id;

    String fileId;

    String userId;

    String mark;

    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime updateDate;

    Long downloads;


}

package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import com.jmal.clouddisk.service.impl.DirectLinkService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Getter
@Setter
@Document(collection = DirectLinkService.COLLECTION_NAME)
@CompoundIndexes({
        @CompoundIndex(name = "mark_1", def = "{'mark': 1}"),
        @CompoundIndex(name = "fileId_1", def = "{'fileId': 1}"),

})
@Entity
@Table(name = DirectLinkService.COLLECTION_NAME,
        indexes = {
                @Index(name = "direct_links_file_id", columnList = "fileId"),
                @Index(name = "direct_links_mark", columnList = "mark"),
        }
)
public class DirectLink extends AuditableEntity implements Reflective {

    @Column(length = 24)
    String fileId;

    @Column(length = 24)
    String userId;

    @Column(length = 16)
    String mark;

    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime updateDate;

    Long downloads;


}

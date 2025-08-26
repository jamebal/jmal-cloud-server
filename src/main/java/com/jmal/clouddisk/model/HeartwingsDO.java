package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * @author jmal
 * @Description 心语记录
 * @Date 2021/3/1 10:19 上午
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Document(collection = "heartwings")
@CompoundIndexes({
        @CompoundIndex(name = "createTime_1", def = "{'createTime': 1}"),
})
@Entity
@Table(name = "heartwings")
public class HeartwingsDO extends AuditableEntity implements Reflective {

    /**
     * 创建者 userId
     */
    private String creator;

    /**
     * 创建者 username
     */
    private String username;

    /**
     * 心语
     */
    private String heartwings;

    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}

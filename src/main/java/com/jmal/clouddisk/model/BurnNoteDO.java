package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableTimeEntity;
import com.jmal.clouddisk.service.impl.BurnNoteService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * 阅后即焚笔记模型
 */
@Getter
@Setter
@Document(collection = BurnNoteService.TABLE_NAME)
@Entity
@Table(name = BurnNoteService.TABLE_NAME)
@CompoundIndexes({
        @CompoundIndex(name = "userId_1", def = "{'userId': 1}"),
        @CompoundIndex(name = "expireAt_1", def = "{'expireAt': 1}"),
        @CompoundIndex(name = "updatedTime_1", def = "{'updatedTime': 1}"),
})
public class BurnNoteDO extends AuditableTimeEntity implements Reflective {

    /**
     * 创建者用户ID
     */
    @Column(length = 24)
    private String userId;

    /**
     * 加密后的内容（文本）或文件元数据（JSON）
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String encryptedContent;

    /**
     * 是否为文件类型
     */
    @Column(name = "is_file", nullable = false)
    private Boolean isFile = false;

    /**
     * 文件分片总数（仅文件类型）
     */
    @Column(name = "total_chunks")
    private Integer totalChunks;

    /**
     * 文件原始大小（字节）
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * 剩余查看次数
     * null 表示使用时间过期而非次数限制
     */
    private Integer viewsLeft;

    /**
     * 过期时间
     * null 表示使用查看次数而非时间限制
     */
    private Instant expireAt;

}

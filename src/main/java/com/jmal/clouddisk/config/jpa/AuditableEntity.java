package com.jmal.clouddisk.config.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity {

    @Id
    @Column(name = "id", length = 24)
    public String id;

    @CreatedDate
    @Column(name = "created_time", updatable = false)
    private LocalDateTime createdTime;

    @LastModifiedDate
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    @PrePersist
    protected void onPrePersist() {
        // 统一的ID生成策略
        if (this.id == null || this.id.isEmpty()) {
            this.id = generateObjectId();
        }
    }

    /**
     * 生成ObjectId
     */
    private String generateObjectId() {
        return new ObjectId().toHexString();
    }

}

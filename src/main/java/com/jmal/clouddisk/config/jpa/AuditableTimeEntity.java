package com.jmal.clouddisk.config.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

@Getter
@Setter
@MappedSuperclass
@Conditional(RelationalDataSourceCondition.class)
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableTimeEntity implements Identifiable {

    @Id
    @Column(name = "id", length = 24, columnDefinition = "varchar(24)")
    public String id;

    @CreatedDate
    @Column(name = "created_time", updatable = false)
    private Instant createdTime;

    @LastModifiedDate
    @Column(name = "updated_time")
    private Instant updatedTime;

    @PrePersist
    protected void onPrePersist() {
        if (this.id == null || this.id.isEmpty()) {
            this.id = new ObjectId().toHexString();
        }
    }

}

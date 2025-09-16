package com.jmal.clouddisk.config.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// common模块
@Getter
@Setter
@MappedSuperclass
@Conditional(RelationalDataSourceCondition.class)
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditablePerformanceEntity implements Identifiable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long p_id;

    @Column(name = "public_id", unique = true, nullable = false)
    private String publicId;

    @Override
    @Transient
    public String getId() {
        return this.publicId;
    }

    @Override
    public void setId(String id) {
        this.publicId = id;
    }

    @PrePersist
    protected void onPrePersist() {
        if (this.publicId == null || this.publicId.isEmpty()) {
            this.publicId = new ObjectId().toHexString();
        }
    }
}

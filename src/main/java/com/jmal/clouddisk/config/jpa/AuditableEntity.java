package com.jmal.clouddisk.config.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Getter
@Setter
@MappedSuperclass
@Conditional(RelationalDataSourceCondition.class)
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditableEntity implements Identifiable {

    @Id
    @Column(name = "id", length = 24, columnDefinition = "varchar(24)")
    public String id;

    @PrePersist
    protected void onPrePersist() {
        if (this.id == null || this.id.isEmpty()) {
            this.id = new ObjectId().toHexString();
        }
    }

}

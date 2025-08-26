package com.jmal.clouddisk.model.file;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "articles")
public class ArticleDO extends AuditableEntity implements Reflective {
    private String fileId;
    private Boolean release;
    private Boolean alonePage;
    private Integer pageSort;
    private String draft;
    private String cover;
    private String slug;
    private List<String> categoryIds;
    private List<String> tagIds;
}

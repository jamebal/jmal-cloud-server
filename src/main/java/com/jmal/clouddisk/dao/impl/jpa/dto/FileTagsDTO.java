package com.jmal.clouddisk.dao.impl.jpa.dto;

import com.jmal.clouddisk.model.Tag;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Setter
@Getter
public class FileTagsDTO {
    private final String id;
    private Set<Tag> tags;

    public FileTagsDTO(String id, Set<Tag> tags) {
        this.id = id;
        this.tags = tags;
    }

}

package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@TypeAlias("")
public class Tag implements Reflective {
    String tagId;
    String name;
    String color;

    public Tag(String tagId, String oldName, String blue) {
        this.tagId = tagId;
        this.name = oldName;
        this.color = blue;
    }

    public Tag() {

    }
}

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
}

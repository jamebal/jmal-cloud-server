package com.jmal.clouddisk.model;

import lombok.Data;
import org.springframework.data.annotation.TypeAlias;

@Data
@TypeAlias("")
public class Tag {
    String tagId;
    String name;
    String color;
}

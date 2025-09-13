package com.jmal.clouddisk.model.file.dto;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.model.Tag;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

/**
 * @author jmal
 * @Description 文件模型基类
 * @Date 2020/11/12 2:05 下午
 */
@Getter
@Setter
@NoArgsConstructor
public class FileBaseTagsDTO extends FileBaseDTO implements Reflective {

    Set<Tag> tags = new HashSet<>();


    public FileBaseTagsDTO(String id, Set<Tag> tags) {
        this.id = id;
        this.tags = tags;
    }


}

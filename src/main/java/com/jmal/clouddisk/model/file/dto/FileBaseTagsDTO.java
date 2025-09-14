package com.jmal.clouddisk.model.file.dto;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.model.Tag;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * @author jmal
 * @Description 文件模型基类
 * @Date 2020/11/12 2:05 下午
 */
@Getter
@Setter
@NoArgsConstructor
public class FileBaseTagsDTO extends FileBaseDTO implements Reflective {

    List<Tag> tags;


    public FileBaseTagsDTO(String id, List<Tag> tags) {
        this.id = id;
        this.tags = tags;
    }


}

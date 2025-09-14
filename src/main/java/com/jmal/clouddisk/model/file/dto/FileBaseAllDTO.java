package com.jmal.clouddisk.model.file.dto;

import com.jmal.clouddisk.config.Reflective;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @author jmal
 * @Description 文件模型基类
 * @Date 2020/11/12 2:05 下午
 */
@Getter
@Setter
@NoArgsConstructor
public class FileBaseAllDTO extends FileBaseDTO implements Reflective {

    private String suffix;
    private Long size;
    private String contentType;


    public FileBaseAllDTO(String id, String name, String path, String userId, Boolean isFolder, String suffix, Long size, String contentType) {
        super(id, name, path, userId, isFolder);
        this.size = size;
        this.suffix = suffix;
        this.contentType = contentType;
    }


}

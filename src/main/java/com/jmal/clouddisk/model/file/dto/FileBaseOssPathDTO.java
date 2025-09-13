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
public class FileBaseOssPathDTO extends FileBaseDTO implements Reflective {

    String ossFolder;

    public FileBaseOssPathDTO(String id, String name, String path, String userId, Boolean isFolder, String ossFolder) {
        this.id = id;
        this.name = name;
        this.path = path;
        this.userId = userId;
        this.isFolder = isFolder;
        this.ossFolder = ossFolder;
    }

}

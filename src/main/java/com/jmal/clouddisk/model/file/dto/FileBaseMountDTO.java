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
public class FileBaseMountDTO extends FileBaseDTO implements Reflective {

    private String mountFileId;


    public FileBaseMountDTO(String id, String name, String path, String userId, Boolean isFolder, String mountFileId) {
        super(id, name, path, userId, isFolder);
        this.mountFileId = mountFileId;
    }


}

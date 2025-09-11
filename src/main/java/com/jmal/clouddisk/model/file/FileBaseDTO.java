package com.jmal.clouddisk.model.file;

import com.jmal.clouddisk.config.Reflective;
import lombok.Getter;
import lombok.Setter;

/**
 * @author jmal
 * @Description 文件模型基类
 * @Date 2020/11/12 2:05 下午
 */
@Getter
@Setter
public class FileBaseDTO implements Reflective {

    String name;
    String path;
    String userId;

    public FileBaseDTO(String name, String path, String userId) {
        this.name = name;
        this.path = path;
        this.userId = userId;
    }

}

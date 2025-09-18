package com.jmal.clouddisk.model.file.dto;

import com.jmal.clouddisk.config.Reflective;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Objects;

/**
 * @author jmal
 * @Description 文件模型基类
 * @Date 2020/11/12 2:05 下午
 */
@Getter
@Setter
@NoArgsConstructor
public class FileBaseEtagDTO extends FileBaseDTO implements Reflective {

    private String etag;

    public FileBaseEtagDTO(String id, String name, String path, String userId) {
        super(id, name, path, userId);
    }

    public FileBaseEtagDTO(String id, String name, String path, String userId, String etag) {
        super(id, name, path, userId);
        this.etag = etag;
    }

    public FileBaseEtagDTO(String id, String name, String path, String userId, Boolean isFolder, String etag) {
        super(id, name, path, userId, isFolder);
        this.etag = etag;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final FileBaseEtagDTO other = (FileBaseEtagDTO) obj;
        return Objects.equals(this.id, other.id);
    }

    @Override
    public int hashCode() {
        int hash = 12;
        hash = 36 * hash + (this.id != null ? this.id.hashCode() : 0);
        return hash;
    }


}

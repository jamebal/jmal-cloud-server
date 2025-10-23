package com.jmal.clouddisk.model.file.dto;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.model.Tag;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jmal
 * @Description 文件模型基类
 * @Date 2020/11/12 2:05 下午
 */
@Getter
@Setter
@NoArgsConstructor
public class FileBaseLuceneDTO extends FileBaseDTO implements Reflective {

    private Boolean isFavorite;
    private String remark;
    private List<Tag> tags;
    private List<String> tagIds;
    private String etag;
    private Long size;
    private LocalDateTime uploadDate;

    public FileBaseLuceneDTO(String id, String name, String path, String userId, Boolean isFolder, Boolean isFavorite, String remark, List<Tag> tags, List<String> tagIds, String etag, Long size, LocalDateTime uploadDate) {
        super(id, name, path, userId, isFolder);
        this.isFavorite = isFavorite;
        this.remark = remark;
        this.tags = tags;
        this.tagIds = tagIds;
        this.etag = etag;
        this.size = size;
        this.uploadDate = uploadDate;
    }


}

package com.jmal.clouddisk.model.file.dto;

import com.jmal.clouddisk.media.VideoInfoDO;
import com.jmal.clouddisk.model.file.ExifInfo;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class UpdateFile {
    private ExifInfo exif;
    private VideoInfoDO video;
    private String contentType;
    private String suffix;
    private LocalDateTime updateDate;

    public boolean isNotEmpty() {
        return exif != null ||
                video != null ||
                contentType != null ||
                suffix != null ||
                updateDate != null;
    }
}

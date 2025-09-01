package com.jmal.clouddisk.model.file;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import com.jmal.clouddisk.util.TimeUntils;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * @author jmal
 * @Description 文件模型基类
 * @Date 2020/11/12 2:05 下午
 */
@Getter
@Setter
@MappedSuperclass
public class FileBase extends AuditableEntity implements Reflective {

    /***
     * 是否为文件夹
     */
    private Boolean isFolder;
    /***
     * 文件名称
     */
    private String name;
    /***
     * 文件MD5值
     */
    private String md5;
    /***
     * 文件大小
     */
    private Long size;
    /***
     * 文件类型
     */
    private String contentType;
    /***
     * 上传时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime uploadDate;
    /***
     * 修改时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateDate;

    /***
     * 格式化文件更新时间
     * @return yyyy 年 MM 月 dd 日 HH:mm:ss
     */
    public String updateTime(){
       return updateDate.format(TimeUntils.UPDATE_FORMAT_TIME);
    }

    /***
     * 格式化文件上传时间
     * @return yyyy 年 MM 月 dd 日
     */
    public String uploadTime(){
        return uploadDate.format(TimeUntils.UPLOAD_FORMAT_TIME);
    }

}

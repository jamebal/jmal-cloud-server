package com.jmal.clouddisk.model.file;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jmal.clouddisk.config.Reflective;
import jakarta.persistence.Embeddable;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/**
 * 照片EXIF信息
 */
@Data
@Embeddable
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExifInfo implements Reflective {
    /**
     * 设备制造商
     */
    private String make;
    /**
     * 设备型号
     */
    private String model;
    /**
     * 分辨率
     */
    private String resolution;
    /**
     * 内容创作者
     */
    private String software;
    /**
     * 内容创建时间
     */
    @JsonFormat(shape=JsonFormat.Shape.STRING, pattern="yyyy-MM-dd HH:mm:ss")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime dateTimeOriginal;
    /**
     * 光圈值
     */
    private Double aperture;
    /**
     * 光圈数
     */
    private String fNumber;
    /**
     * 曝光时间
     */
    private Double exposureTime;
    /**
     * ISO感光度
     */
    private Integer isoEquivalent;
    /**
     * 曝光程序
     */
    private String exposureProgram;
    /**
     * 测光模式
     */
    private String meteringMode;
    /**
     * 白平衡
     */
    private String whiteBalanceMode;
    /**
     * 焦距
     */
    private Double focalLength;
    /**
     * 闪光灯
     */
    private String flash;
    /**
     * 经度
     */
    private Double longitude;
    /**
     * 纬度
     */
    private Double latitude;

    public String toString() {
        return "ExifInfo{" +
                "make='" + make + '\'' +
                ", model='" + model + '\'' +
                ", resolution='" + resolution + '\'' +
                ", software='" + software + '\'' +
                ", dateTimeOriginal=" + dateTimeOriginal +
                ", aperture=" + aperture +
                ", fNumber='" + fNumber + '\'' +
                ", exposureTime=" + exposureTime +
                ", isoEquivalent=" + isoEquivalent +
                ", exposureProgram='" + exposureProgram + '\'' +
                ", meteringMode='" + meteringMode + '\'' +
                ", whiteBalanceMode='" + whiteBalanceMode + '\'' +
                ", focalLength=" + focalLength +
                ", flash='" + flash + '\'' +
                ", longitude=" + longitude +
                ", latitude=" + latitude +
                '}';
    }
}

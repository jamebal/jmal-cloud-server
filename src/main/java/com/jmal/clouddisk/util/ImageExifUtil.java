package com.jmal.clouddisk.util;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.NumberUtil;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDescriptor;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.jmal.clouddisk.model.ExifInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Date;

@Slf4j
public class ImageExifUtil {

    public static ExifInfo getExif(File file) {
        if (file == null) {
            return null;
        }
        if (!file.exists()) {
            return null;
        }
        try {
            ExifInfo exifInfo = null;
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            // 获取图片基础信息
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null) {
                exifInfo = new ExifInfo();
                // 设备制造商
                exifInfo.setMake(directory.getString(ExifSubIFDDirectory.TAG_MAKE));
                // 设备型号
                exifInfo.setModel(directory.getString(ExifSubIFDDirectory.TAG_MODEL));
                // 分辨率
                exifInfo.setResolution(directory.getString(ExifSubIFDDirectory.TAG_X_RESOLUTION) + "x" + directory.getString(ExifSubIFDDirectory.TAG_Y_RESOLUTION));
                // 内容创作者
                exifInfo.setSoftware(directory.getString(ExifSubIFDDirectory.TAG_SOFTWARE));
            }

            // 获取图片的Exif信息
            ExifSubIFDDirectory exifDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exifDirectory != null) {
                if (exifInfo == null) {
                    exifInfo = new ExifInfo();
                }
                ExifSubIFDDescriptor descriptor = new ExifSubIFDDescriptor(exifDirectory);
                // 内容创建时间
                Date date = exifDirectory.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (date != null) {
                    exifInfo.setDateTimeOriginal(LocalDateTimeUtil.of(date));
                }
                // 光圈值
                exifInfo.setAperture(NumberUtil.round(exifDirectory.getDouble(ExifSubIFDDirectory.TAG_APERTURE), 4).doubleValue());
                // 光圈数
                exifInfo.setFNumber("f/" + exifDirectory.getString(ExifSubIFDDirectory.TAG_FNUMBER));
                // 曝光时间
                exifInfo.setExposureTime(exifDirectory.getDouble(ExifSubIFDDirectory.TAG_EXPOSURE_TIME));
                // ISO感光度
                exifInfo.setIsoEquivalent(exifDirectory.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT));
                // 曝光程序
                exifInfo.setExposureProgram(descriptor.getExposureProgramDescription());
                // 测光模式
                exifInfo.setMeteringMode(descriptor.getMeteringModeDescription());
                // 白平衡
                exifInfo.setWhiteBalanceMode(descriptor.getWhiteBalanceModeDescription());
                // 焦距
                exifInfo.setFocalLength(exifDirectory.getDouble(ExifSubIFDDirectory.TAG_FOCAL_LENGTH));
                // 闪光灯
                exifInfo.setFlash(getFlashDescription(exifDirectory.getInteger(ExifSubIFDDirectory.TAG_FLASH)));
            }
            // 获取GPS信息
            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDirectory != null) {
                if (exifInfo == null) {
                    exifInfo = new ExifInfo();
                }
                // 经度
                exifInfo.setLongitude(gpsDirectory.getGeoLocation().getLongitude());
                // 纬度
                exifInfo.setLatitude(gpsDirectory.getGeoLocation().getLatitude());
            }
            return exifInfo;
        } catch (Exception e) {
            // 获取图片EXIF信息失败
            log.warn("获取图片EXIF信息失败: {}", e.getMessage(), e);
        }
        return null;
    }

    /**
     * 获取闪光灯描述
     * @param value 闪光灯值
     * @return 闪光灯描述
     */
    public static String getFlashDescription(Integer value) {
        if (value == null)
            return null;
        StringBuilder sb = new StringBuilder();
        if ((value & 0x1) != 0)
            sb.append("是");
        else
            sb.append("无");
        return sb.toString();
    }
}

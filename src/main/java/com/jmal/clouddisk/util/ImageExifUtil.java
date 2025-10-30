package com.jmal.clouddisk.util;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.NumberUtil;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDescriptor;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.jmal.clouddisk.model.file.ExifInfo;
import com.jmal.clouddisk.service.Constants;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.Date;
import java.util.TimeZone;

@Slf4j
public class ImageExifUtil {

    public static boolean isImageType(String contentType, String suffix) {
        return contentType.startsWith(Constants.CONTENT_TYPE_IMAGE) && needToHandle(suffix);
    }

    public static boolean needToHandle(String suffix) {
        return (!"ico".equals(suffix) && !"svg".equals(suffix));
    }

    public static ExifInfo getExif(File file) {
        ExifInfo exifInfo = new ExifInfo();
        if (file == null) {
            return exifInfo;
        }
        if (!file.exists()) {
            return exifInfo;
        }
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(file);
            // 获取图片基础信息
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory != null) {
                exifInfo = new ExifInfo();
                // 设备制造商
                if (directory.containsTag(ExifIFD0Directory.TAG_MAKE)) {
                    exifInfo.setMake(directory.getString(ExifIFD0Directory.TAG_MAKE));
                }
                // 设备型号
                if (directory.containsTag(ExifIFD0Directory.TAG_MODEL)) {
                    exifInfo.setModel(directory.getString(ExifIFD0Directory.TAG_MODEL));
                }
                // 分辨率
                if (directory.containsTag(ExifIFD0Directory.TAG_X_RESOLUTION) && directory.containsTag(ExifIFD0Directory.TAG_Y_RESOLUTION)) {
                    exifInfo.setResolution(directory.getString(ExifIFD0Directory.TAG_X_RESOLUTION) + "x" + directory.getString(ExifIFD0Directory.TAG_Y_RESOLUTION));
                }
                // 内容创作者
                if (directory.containsTag(ExifIFD0Directory.TAG_SOFTWARE)) {
                    exifInfo.setSoftware(directory.getString(ExifIFD0Directory.TAG_SOFTWARE));
                }
            }

            // 获取图片的Exif信息
            ExifSubIFDDirectory exifDirectory = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exifDirectory != null) {
                ExifSubIFDDescriptor descriptor = new ExifSubIFDDescriptor(exifDirectory);
                // 内容创建时间
                if (exifDirectory.containsTag(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL)) {
                    Date date = exifDirectory.getDateOriginal(TimeZone.getDefault());
                    if (date != null) {
                        exifInfo.setDateTimeOriginal(LocalDateTimeUtil.of(date));
                    }
                }
                // 光圈值
                if (exifDirectory.containsTag(ExifSubIFDDirectory.TAG_APERTURE)) {
                    exifInfo.setAperture(NumberUtil.round(exifDirectory.getDouble(ExifSubIFDDirectory.TAG_APERTURE), 4).doubleValue());
                }
                // 光圈数
                if (exifDirectory.containsTag(ExifSubIFDDirectory.TAG_FNUMBER)) {
                    exifInfo.setFNumber("f/" + exifDirectory.getString(ExifSubIFDDirectory.TAG_FNUMBER));
                }
                // 曝光时间
                if (exifDirectory.containsTag(ExifSubIFDDirectory.TAG_EXPOSURE_TIME)) {
                    exifInfo.setExposureTime(exifDirectory.getDouble(ExifSubIFDDirectory.TAG_EXPOSURE_TIME));
                }
                // ISO感光度
                if (exifDirectory.containsTag(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT)) {
                    exifInfo.setIsoEquivalent(exifDirectory.getInteger(ExifSubIFDDirectory.TAG_ISO_EQUIVALENT));
                }
                // 曝光程序
                if (exifDirectory.containsTag(ExifSubIFDDirectory.TAG_EXPOSURE_PROGRAM)) {
                    exifInfo.setExposureProgram(descriptor.getExposureProgramDescription());
                }
                // 测光模式
                if (exifDirectory.containsTag(ExifSubIFDDirectory.TAG_METERING_MODE)) {
                    exifInfo.setMeteringMode(descriptor.getMeteringModeDescription());
                }
                // 白平衡
                if (exifDirectory.containsTag(ExifSubIFDDirectory.TAG_WHITE_BALANCE_MODE)) {
                    exifInfo.setWhiteBalanceMode(descriptor.getWhiteBalanceModeDescription());
                }
                // 焦距
                if (exifDirectory.containsTag(ExifSubIFDDirectory.TAG_FOCAL_LENGTH)) {
                    exifInfo.setFocalLength(exifDirectory.getDouble(ExifSubIFDDirectory.TAG_FOCAL_LENGTH));
                }
                // 闪光灯
                if (exifDirectory.containsTag(ExifSubIFDDirectory.TAG_FLASH)) {
                    exifInfo.setFlash(getFlashDescription(exifDirectory.getInteger(ExifSubIFDDirectory.TAG_FLASH)));
                }
            }
            // 获取GPS信息
            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDirectory != null) {
                if (gpsDirectory.getGeoLocation() != null) {
                    // 经度
                    exifInfo.setLongitude(gpsDirectory.getGeoLocation().getLongitude());
                }
                if (gpsDirectory.getGeoLocation() != null) {
                    // 纬度
                    exifInfo.setLatitude(gpsDirectory.getGeoLocation().getLatitude());
                }
            }
            return exifInfo;
        } catch (Exception e) {
            // 获取图片EXIF信息失败
            log.debug("获取图片EXIF信息失败: {}, {}", e.getMessage(), file);
        }
        return exifInfo;
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

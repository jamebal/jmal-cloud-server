package com.jmal.clouddisk;

import com.jmal.clouddisk.model.ExifInfo;
import com.jmal.clouddisk.util.ImageExifUtil;

import java.io.File;

public class ExifReader {
    public static void main(String[] args) {
        try {
            // File file = new File("/Users/jmal/Pictures/壁纸/WechatIMG15108.jpeg");
            // File file = new File("/Users/jmal/Pictures/壁纸/WechatIMG15107.jpeg");
            // File file = new File("/Users/jmal/Downloads/output.png");
            File file = new File("/Users/jmal/Downloads/IMG_0871.HEIC");
            // File file = new File("/Users/jmal/Downloads/fox.profile0.10bpc.yuv420.avif");
            // File file = new File("/Users/jmal/Downloads/IMG_0872.HEIC");

            ExifInfo exifInfo = ImageExifUtil.getExif(file);
            if (exifInfo != null) {
                System.out.println(exifInfo);
            } else {
                System.out.println("No Exif data found.");
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

}

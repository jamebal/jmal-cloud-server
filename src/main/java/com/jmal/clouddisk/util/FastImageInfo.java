package com.jmal.clouddisk.util;

import cn.hutool.core.lang.Console;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.*;

/**
 * 快速获取图片的分辨率
 */
@Data
@Slf4j
public class FastImageInfo {
    private int height;
    private int width;
    private String mimeType;

    public FastImageInfo(File file) {
        try (InputStream is = new FileInputStream(file)) {
            processStream(is);
        } catch (IOException e) {
            log.warn("获取图片尺寸失败, {}", file);
        }
    }

    private void processStream(InputStream is) throws IOException {
        int c1 = is.read();
        int c2 = is.read();
        int c3 = is.read();

        mimeType = null;
        width = height = -1;

        if (c1 == 'G' && c2 == 'I' && c3 == 'F') {
            // GIF
            is.skip(3);
            width = readInt(is, 2, false);
            height = readInt(is, 2, false);
            mimeType = "image/gif";
        } else if (c1 == 0xFF && c2 == 0xD8) {
            // JPG
            while (c3 == 255) {
                int marker = is.read();
                int len = readInt(is, 2, true);
                if (marker == 192 || marker == 193 || marker == 194) {
                    is.skip(1);
                    height = readInt(is, 2, true);
                    width = readInt(is, 2, true);
                    mimeType = "image/jpeg";
                    break;
                }
                is.skip(len - 2);
                c3 = is.read();
            }
        } else if (c1 == 137 && c2 == 80 && c3 == 78) {
            // PNG
            is.skip(15);
            width = readInt(is, 2, true);
            is.skip(2);
            height = readInt(is, 2, true);
            mimeType = "image/png";
        } else if (c1 == 66 && c2 == 77) {
            // BMP
            is.skip(15);
            width = readInt(is, 2, false);
            is.skip(2);
            height = readInt(is, 2, false);
            mimeType = "image/bmp";
        } else if (c1 == 'R' && c2 == 'I' && c3 == 'F') {
            // WEBP
            byte[] bytes = new byte[1024];
            is.read(bytes, 0 ,27);
            width = ((int) bytes[24] & 0xff) << 8 | ((int) bytes[23] & 0xff);
            height = ((int) bytes[26] & 0xff) << 8 | ((int) bytes[25] & 0xff);
            mimeType = "image/webp";
        } else if (c1 == 'f' && c2 == 't' && c3 == 'y') {
            // HEIC/AVIF
            is.skip(5);
            byte[] bytes = new byte[4];
            is.read(bytes, 0, 4);
            String type = new String(bytes);
            if ("heic".equals(type) || "avif".equals(type)) {
                mimeType = type.equals("heic") ? "image/heic" : "image/avif";
                is.skip(16);
                width = readInt(is, 2, true);
                is.skip(2);
                height = readInt(is, 2, true);
            }
        } else {
            //TIFF
            int c4 = is.read();
            if ((c1 == 'M' && c2 == 'M' && c3 == 0 && c4 == 42) || (c1 == 'I' && c2 == 'I' && c3 == 42 && c4 == 0)) {
                boolean bigEndian = c1 == 'M';
                int ifd = 0;
                int entries;
                ifd = readInt(is, 4, bigEndian);
                is.skip(ifd - 8);
                entries = readInt(is, 2, bigEndian);
                for (int i = 1; i <= entries; i++) {
                    int tag = readInt(is, 2, bigEndian);
                    int fieldType = readInt(is, 2, bigEndian);
                    int valOffset;
                    if ((fieldType == 3 || fieldType == 8)) {
                        valOffset = readInt(is, 2, bigEndian);
                        is.skip(2);
                    } else {
                        valOffset = readInt(is, 4, bigEndian);
                    }
                    if (tag == 256) {
                        width = valOffset;
                    } else if (tag == 257) {
                        height = valOffset;
                    }
                    if (width != -1 && height != -1) {
                        mimeType = "image/tiff";
                        break;
                    }
                }
            }
        }
        if (mimeType == null) {
            throw new IOException("Unsupported image type");
        }
    }

    private int readInt(InputStream is, int noOfBytes, boolean bigEndian) throws IOException {
        int ret = 0;
        int sv = bigEndian ? ((noOfBytes - 1) * 8) : 0;
        int cnt = bigEndian ? -8 : 8;
        for (int i = 0; i < noOfBytes; i++) {
            ret |= is.read() << sv;
            sv += cnt;
        }
        return ret;
    }

    @Override
    public String toString() {
        return "MIME Type : " + mimeType + "\t Width : " + width
                + "\t Height : " + height;
    }

    public static void main(String[] args) throws IOException {
        long s1 = System.currentTimeMillis();
        String path = "/Users/jmal/Pictures/壁纸/WechatIMG15108.jpeg";
        FastImageInfo imageInfo = new FastImageInfo(new File(path));
        int width = imageInfo.getWidth();
        int height = imageInfo.getHeight();
        Console.log(width," x ", height);
        long e1 = System.currentTimeMillis();
        Console.log(e1 - s1 + "ms");
    }
}

package com.jmal.clouddisk.util;

import java.io.FileInputStream;
import java.io.IOException;

public class DetectArchiveType {

    public static String detectType(String filePath) throws IOException {
        try (FileInputStream fis = new FileInputStream(filePath)) {
            byte[] header = new byte[32];
            int bytesRead = fis.read(header);

            if (bytesRead < 2) return "unknown";

            // GZIP (.gz, .tar.gz)
            if (header[0] == (byte)0x1F && header[1] == (byte)0x8B) {
                return "gzip";
            }

            // ZIP (.zip, .docx, .xlsx, .jar, etc.)
            if (bytesRead >= 4 && header[0] == (byte)0x50 && header[1] == (byte)0x4B &&
                    (header[2] == (byte)0x03 || header[2] == (byte)0x05 || header[2] == (byte)0x07) &&
                    (header[3] == (byte)0x04 || header[3] == (byte)0x06 || header[3] == (byte)0x08)) {
                return "zip";
            }

            // 7z (.7z)
            if (bytesRead >= 6 && header[0] == (byte)0x37 && header[1] == (byte)0x7A &&
                    header[2] == (byte)0xBC && header[3] == (byte)0xAF &&
                    header[4] == (byte)0x27 && header[5] == (byte)0x1C) {
                return "7z";
            }

            // RAR (.rar)
            if (bytesRead >= 7 &&
                    header[0] == (byte)0x52 && header[1] == (byte)0x61 &&
                    header[2] == (byte)0x72 && header[3] == (byte)0x21 &&
                    header[4] == (byte)0x1A && header[5] == (byte)0x07 &&
                    header[6] == (byte)0x00) {
                return "rar";
            }
            // RAR5 (.rar)
            if (bytesRead >= 8 &&
                    header[0] == (byte)0x52 && header[1] == (byte)0x61 &&
                    header[2] == (byte)0x72 && header[3] == (byte)0x21 &&
                    header[4] == (byte)0x1A && header[5] == (byte)0x07 &&
                    header[6] == (byte)0x01 && header[7] == (byte)0x00) {
                return "rar5";
            }

            // BZip2 (.bz2)
            if (bytesRead >= 3 &&
                    header[0] == (byte)0x42 && header[1] == (byte)0x5A && header[2] == (byte)0x68) {
                return "bzip2";
            }

            // XZ (.xz)
            if (bytesRead >= 6 &&
                    header[0] == (byte)0xFD && header[1] == (byte)0x37 &&
                    header[2] == (byte)0x7A && header[3] == (byte)0x58 &&
                    header[4] == (byte)0x5A && header[5] == (byte)0x00) {
                return "xz";
            }

            // TAR: 没有魔数，只能靠扩展名/内容
            if (filePath.endsWith(".tar")) {
                return "tar";
            }
            // Z (.Z)
            if (header[0] == (byte) 0x1F && header[1] == (byte) 0x9D) {
                return "compress (.Z)";
            }

            // LZ4
            if (bytesRead >= 4 && header[0] == (byte)0x04 && header[1] == (byte)0x22 &&
                    header[2] == (byte)0x4D && header[3] == (byte)0x18) {
                return "lz4";
            }

            // LZH (.lzh)
            if (header[0] == (byte) 0x2D && header[1] == (byte) 0x6C) {
                return "lzh";
            }

            // CAB (.cab)
            if (bytesRead >= 4 && header[0] == (byte)0x4D && header[1] == (byte)0x53 &&
                    header[2] == (byte)0x43 && header[3] == (byte)0x46) {
                return "cab";
            }

            // AR (.ar)
            if (bytesRead >= 8 &&
                    header[0] == (byte)0x21 && header[1] == (byte)0x3C &&
                    header[2] == (byte)0x61 && header[3] == (byte)0x72 &&
                    header[4] == (byte)0x63 && header[5] == (byte)0x68 &&
                    header[6] == (byte)0x3E && header[7] == (byte)0x0A) {
                return "ar";
            }

            return "unknown";
        }
    }

    public static void main(String[] args) throws IOException {
        String file = "/Users/jmal/studio/myProject/github/jmal-cloud-server/target/jmalcloud-2.15.0.jar";
        String type = detectType(file);
        System.out.println("Type = " + type);
    }
}

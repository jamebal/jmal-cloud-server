package com.jmal.clouddisk.media;

public class VideoInfoUtil {

    /**
     * 将比特率转换为可读的格式
     * @param bitrate 比特率
     * @return 可读的比特率格式
     */
    public static String convertBitrateToReadableFormat(long bitrate) {
        if (bitrate < 1000) {
            return bitrate + " bps";
        } else if (bitrate < 1000000) {
            return String.format("%.2f Kbps", bitrate / 1000.0);
        } else {
            return String.format("%.2f Mbps", bitrate / 1000000.0);
        }
    }

    /**
     * 将视频时长换为可读的格式
     * @param timestamp 视频时长(秒)
     * @param milliseconds 是否包含毫秒
     * @return 可读的视频时长格式
     */
    public static String formatTimestamp(int timestamp, boolean milliseconds) {
        int hours = timestamp / 3600;
        int minutes = (timestamp % 3600) / 60;
        int seconds = timestamp % 60;
        if (milliseconds) {
            return String.format("%02d:%02d:%02d.000", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
    }

    public static void main(String[] args) {
        long bitrate = 200000; // 示例比特率
        String readableBitrate = convertBitrateToReadableFormat(bitrate);
        System.out.println("可读的比特率格式为: " + readableBitrate);
        int duration = 3600; // 示例视频时长
        String readableDuration = formatTimestamp(duration, false);
        System.out.println("可读的视频时长格式为: " + readableDuration);
    }

}

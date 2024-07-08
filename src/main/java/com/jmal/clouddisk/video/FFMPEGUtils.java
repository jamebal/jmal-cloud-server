package com.jmal.clouddisk.video;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.NumberUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

@Slf4j
public class FFMPEGUtils {

    /**
     * h5播放器支持的视频格式
     */
    private static final String[] WEB_SUPPORTED_FORMATS = {"mp4", "webm", "ogg", "flv", "hls", "mkv"};

    /**
     * 获取VTT文件间隔
     *
     * @param videoInfo         视频信息
     * @param vttThumbnailCount 期望的缩略图数量
     * @return VTT文件间隔
     */
    static int getVttInterval(VideoInfo videoInfo, Integer vttThumbnailCount) {
        // 期望的缩略图数量默认为60张
        int videoDuration = videoInfo.getDuration();
        // 计算缩略图间隔
        if (videoDuration <= vttThumbnailCount) {
            return 1;
        }
        return videoDuration / vttThumbnailCount;
    }

    /**
     * 生成VTT文件
     * @param videoInfo 视频信息
     * @param interval 缩略图间隔
     * @param vttFilePath VTT文件路径
     * @param thumbnailImagePath 最终缩略图路径
     */
    static void generateVTT(VideoInfo videoInfo, int interval, String vttFilePath, String thumbnailImagePath, int columns) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(vttFilePath))) {
            writer.write("WEBVTT\n\n");
            int expectedThumbnails = videoInfo.getDuration() / interval;
            for (int i = 0; i < expectedThumbnails; i++) {
                String startTime = VideoInfoUtil.formatTimestamp(i * interval, true);
                String endTime = VideoInfoUtil.formatTimestamp((i + 1) * interval, true);

                int column = i % columns;
                int row = i / columns;
                int thumbWidth = FFMPEGCommand.thumbnailWidth;
                double tHeight = (double) videoInfo.getHeight() / ((double) videoInfo.getWidth() / FFMPEGCommand.thumbnailWidth);
                int thumbHeight = (int) Math.floor(tHeight);
                int x = column * thumbWidth;
                int y = row * thumbHeight;

                writer.write(String.format("%s --> %s\n", startTime, endTime));
                writer.write(String.format("%s#xywh=%d,%d,%d,%d\n\n", thumbnailImagePath, x, y, thumbWidth, thumbHeight));
            }
        } catch (IOException e) {
            log.warn(e.getMessage(), e);
        }
    }

    /**
     * 判断是否需要转码
     *
     * @param videoInfo 视频信息
     * @return 是否需要转码
     */
    static boolean needTranscode(VideoInfo videoInfo, TranscodeConfig transcodeConfig) {
        if (BooleanUtil.isFalse(transcodeConfig.getEnable())) {
            return false;
        }
        // 判断视频码率、高度、帧率是否满足转码条件
        boolean bitrateCond = videoInfo.getBitrate() > 0 && videoInfo.getBitrate() <= transcodeConfig.getBitrateCond();
        boolean heightCond = videoInfo.getHeight() > 0 && videoInfo.getHeight() <= transcodeConfig.getHeightCond();
        boolean frameRateCond = videoInfo.getFrameRate() > 0 && videoInfo.getFrameRate() <= transcodeConfig.getFrameRateCond();
        if (bitrateCond && heightCond && frameRateCond) {
            // 都不满足, 则判断视频格式是否为HTML5 Video Player支持的格式, 不支持则需要转码
            return !isSupportedFormat(videoInfo.getFormat());
        }
        return true;
    }

    /**
     * 判断视频格式是否为HTML5 Video Player支持的格式
     *
     * @param format 视频格式
     * @return 是否支持
     */
    static boolean isSupportedFormat(String format) {
        // HTML5 Video Player支持的视频格式
        String[] formatList = format.split(",");
        for (String f : formatList) {
            for (String supportedFormat : WEB_SUPPORTED_FORMATS) {
                if (f.trim().equalsIgnoreCase(supportedFormat)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 获取转码进度
     * @param videoDuration 视频时长
     * @param line         命令输出信息
     * @return 转码进度
     */
    static double getProgressStr(int videoDuration, String line) {
        String[] parts = line.split("time=")[1].split(" ")[0].split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        int seconds = Integer.parseInt(parts[2].split("\\.")[0]);
        int totalSeconds = hours * 3600 + minutes * 60 + seconds;
        // 计算转码进度百分比
        double progress = (double) totalSeconds / videoDuration * 100;
        if (progress > 100) {
            progress = 100;
        }
        return NumberUtil.round(progress, 2).doubleValue();
    }
}

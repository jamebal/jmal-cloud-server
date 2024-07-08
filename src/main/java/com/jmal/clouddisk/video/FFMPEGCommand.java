package com.jmal.clouddisk.video;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.NumberUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.jmal.clouddisk.service.Constants;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;

import static com.jmal.clouddisk.util.FFMPEGUtils.printErrorInfo;

@Slf4j
public class FFMPEGCommand {

    /**
     * 缩略图宽度
     */
    static final int thumbnailWidth = 128;

    /**
     * 获取视频的分辨率和码率信息
     *
     * @param videoPath 视频路径
     * @return 视频信息
     */
    static VideoInfo getVideoInfo(String videoPath) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffprobe", "-v", "error", "-select_streams", "v:0", "-show_format", "-show_streams", "-of", "json", videoPath);
            Process process = processBuilder.start();
            try (InputStream inputStream = process.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
                String json = reader.lines().collect(Collectors.joining());
                JSONObject jsonObject = JSON.parseObject(json);

                // 获取视频格式信息
                JSONObject formatObject = jsonObject.getJSONObject("format");
                String format = formatObject.getString("format_name");

                // 获取视频时长
                int duration = Convert.toInt(formatObject.get("duration"));

                // 获取视频流信息
                JSONArray streamsArray = jsonObject.getJSONArray("streams");
                if (!streamsArray.isEmpty()) {
                    JSONObject streamObject = streamsArray.getJSONObject(0);
                    int width = streamObject.getIntValue("width");
                    int height = streamObject.getIntValue("height");
                    int rotation = 0;
                    if (streamObject.containsKey("side_data_list")) {
                        for (int i = 0; i < streamObject.getJSONArray("side_data_list").size(); i++) {
                            JSONObject sideData = streamObject.getJSONArray("side_data_list").getJSONObject(i);
                            if (sideData.containsKey("rotation")) {
                                rotation = sideData.getIntValue("rotation");
                                break;
                            }
                        }
                    }
                    // 转换rotation

                    // 获取视频帧率信息
                    String frameRateStr = streamObject.getString("r_frame_rate");
                    double frameRate = parseFrameRate(frameRateStr);

                    int bitrate = streamObject.getIntValue("bit_rate"); // bps
                    return new VideoInfo(videoPath, width, height, format, bitrate, duration, Math.abs(rotation), frameRate);
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                printErrorInfo(processBuilder, process);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return new VideoInfo();
    }

    /**
     * 解析帧率字符串
     *
     * @param frameRateStr 帧率字符串（形如 "30000/1001"）
     * @return 帧率
     */
    static double parseFrameRate(String frameRateStr) {
        if (frameRateStr != null && frameRateStr.contains("/")) {
            String[] parts = frameRateStr.split("/");
            if (parts.length == 2) {
                try {
                    double numerator = Double.parseDouble(parts[0]);
                    double denominator = Double.parseDouble(parts[1]);
                    return NumberUtil.round(numerator / denominator, 2).doubleValue();
                } catch (NumberFormatException e) {
                    log.error("Failed to parse frame rate: {}", frameRateStr, e);
                }
            }
        }
        return 0.0;
    }

    /**
     * 合并缩略图
     * @param inputPattern 输入文件名格式
     * @param outputImage 输出图片
     * @param columns 列数
     * @param rows 行数
     * @return ProcessBuilder
     */
    static ProcessBuilder mergeVTT(String inputPattern, String outputImage, int columns, int rows) {
        ProcessBuilder processBuilder = new ProcessBuilder(
                Constants.FFMPEG,
                "-y",
                "-i", inputPattern,
                "-filter_complex", String.format("tile=%dx%d", columns, rows),
                outputImage
        );
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }

    /**
     * 获取视频封面
     * @param videoPath 视频路径
     * @param outputPath 输出路径
     * @param videoDuration 视频时长
     * @return ProcessBuilder
     */
    static ProcessBuilder getVideoCoverProcessBuilder(String videoPath, String outputPath, int videoDuration) {
        int targetTimestamp = (int) (videoDuration * 0.1);
        String formattedTimestamp = VideoInfoUtil.formatTimestamp(targetTimestamp, false);
        log.debug("\r\nvideoPath: {}, formattedTimestamp: {}", videoPath, formattedTimestamp);
        ProcessBuilder processBuilder = new ProcessBuilder(
                Constants.FFMPEG,
                "-y",
                "-ss", formattedTimestamp,
                "-i", videoPath,
                "-vf", String.format("scale=%s:-2", thumbnailWidth),
                "-frames:v", "1",
                outputPath
        );
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }

    static ProcessBuilder useVideotoolbox(String fileId, Path fileAbsolutePath, int bitrate, int height, String videoCacheDir, String outputPath, int vttInterval, String thumbnailPattern, double frameRate) {
        return new ProcessBuilder(
                Constants.FFMPEG,
                "-hwaccel", "videotoolbox",
                "-i", fileAbsolutePath.toString(),
                "-c:v", "h264_videotoolbox",
                "-profile:v", "main",
                "-pix_fmt", "yuv420p",
                "-level", "4.0",
                "-start_number", "0",
                "-hls_time", "10",
                "-hls_list_size", "0",
                "-vf", "scale=-2:" + height,
                "-b:v", Convert.toStr(bitrate),
                "-preset", "medium",
                "-g", "48",
                "-sc_threshold", "0",
                "-r", String.format("%.2f", frameRate),
                "-f", "hls",
                "-hls_segment_filename", Paths.get(videoCacheDir, fileId + "-%03d.ts").toString(),
                outputPath,
                "-vf", String.format("scale=%s:-2,fps=1/%d", thumbnailWidth, vttInterval),
                thumbnailPattern
        );
    }

    static ProcessBuilder cpuTranscoding(String fileId, Path fileAbsolutePath, int bitrate, int height, String videoCacheDir, String outputPath, int vttInterval, String thumbnailPattern, double frameRate) {

        return new ProcessBuilder(
                Constants.FFMPEG,
                "-i", fileAbsolutePath.toString(),
                "-profile:v", "main",
                "-pix_fmt", "yuv420p",
                "-level", "4.0",
                "-start_number", "0",
                "-hls_time", "10",
                "-hls_list_size", "0",
                "-vf", "scale=-2:" + height,
                "-b:v", Convert.toStr(bitrate),
                "-preset", "medium",
                "-g", "48",
                "-sc_threshold", "0",
                "-r", String.format("%.2f", frameRate),
                "-f", "hls",
                "-hls_segment_filename", Paths.get(videoCacheDir, fileId + "-%03d.ts").toString(),
                outputPath,
                "-vf", String.format("scale=%s:-2,fps=1/%d", thumbnailWidth, vttInterval),
                thumbnailPattern
        );
    }

    static ProcessBuilder useNvencCuda(String fileId, Path fileAbsolutePath, int bitrate, int height, String videoCacheDir, String outputPath, double frameRate) {
        // 使用CUDA硬件加速和NVENC编码器
        return new ProcessBuilder(
                Constants.FFMPEG,
                "-init_hw_device", "cuda=cu:0",
                "-filter_hw_device", "cu",
                "-hwaccel", "cuda",
                "-hwaccel_output_format", "cuda",
                "-threads", "1",
                "-autorotate", "0",
                "-i", fileAbsolutePath.toString(),
                "-autoscale", "0",
                "-map_metadata", "-1",
                "-map_chapters", "-1",
                "-threads", "0",
                "-map", "0:0",
                "-map", "0:1",
                "-map", "-0:s",
                "-codec:v:0", "h264_nvenc",
                "-preset", "p1",
                "-b:v", Convert.toStr(bitrate),
                "-maxrate", Convert.toStr(bitrate),
                "-bufsize", "5643118",
                "-g:v:0", "180",
                "-keyint_min:v:0", "180",
                "-vf", "setparams=color_primaries=bt709:color_trc=bt709:colorspace=bt709,scale_cuda=-2:" + height + ":format=yuv420p",
                "-codec:a:0", "copy",
                "-copyts",
                "-avoid_negative_ts", "disabled",
                "-max_muxing_queue_size", "2048",
                "-r", String.format("%.2f", frameRate),
                "-f", "hls",
                "-max_delay", "5000000",
                "-hls_time", "3",
                "-hls_segment_type", "mpegts",
                "-start_number", "0",
                "-y",
                "-hls_segment_filename", Paths.get(videoCacheDir, fileId + "-%03d.ts").toString(),
                "-hls_playlist_type", "vod",
                "-hls_list_size", "0",
                outputPath
        );
    }

    static ProcessBuilder useNvencCudaVtt(Path fileAbsolutePath, int vttInterval, String thumbnailPattern) {
        // 使用CUDA硬件加速和NVENC编码器
        return new ProcessBuilder(
                Constants.FFMPEG,
                "-init_hw_device", "cuda=cu:0",
                "-filter_hw_device", "cu",
                "-i", fileAbsolutePath.toString(),
                "-y",
                "-vf", String.format("scale=%s:-2,fps=1/%d", thumbnailWidth, vttInterval),
                "-vsync", "vfr",
                thumbnailPattern
        );
    }

    /**
     * 检测是否装有Mac Apple Silicon
     */
    static boolean checkMacAppleSilicon() {
        try {
            Process process = Runtime.getRuntime().exec("ffmpeg -hwaccels");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("videotoolbox")) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /**
     * 检测是否装有NVIDIA显卡驱动
     */
    static boolean checkNvidiaDrive() {
        try {
            Process process = Runtime.getRuntime().exec("nvidia-smi");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Version")) {
                    return true;
                }
            }
        } catch (IOException e) {
            return false;
        }
        return false;
    }

    /**
     * 检查是否没有ffmpeg
     * @return true: 没有ffmpeg
     */
    public static boolean hasNoFFmpeg() {
        try {
            Process process = Runtime.getRuntime().exec("ffmpeg -version");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("ffmpeg version")) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return true;
    }
}

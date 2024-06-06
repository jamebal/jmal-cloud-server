package com.jmal.clouddisk.video;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.lucene.TaskProgressService;
import com.jmal.clouddisk.lucene.TaskType;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.oss.IOssService;
import com.jmal.clouddisk.oss.OssConfigService;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.jmal.clouddisk.util.CaffeineUtil;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.jmal.clouddisk.util.FFMPEGUtils.*;

@Service
@Lazy
@Slf4j
public class VideoProcessService {

    @Autowired
    private FileProperties fileProperties;

    @Autowired
    private IUserService userService;

    @Autowired
    private CommonFileService commonFileService;

    @Autowired
    private TaskProgressService taskProgressService;

    @Resource
    MongoTemplate mongoTemplate;

    private ExecutorService executorService;


    /**
     * h5播放器支持的视频格式
     */
    private final String[] WEB_SUPPORTED_FORMATS = {"mp4", "webm", "ogg", "flv", "hls", "mkv"};

    @PostConstruct
    public void init() {
        int processors = Runtime.getRuntime().availableProcessors() - 1;
        if (processors < 1) {
            processors = 1;
        }
        if (processors > 4) {
            processors = 4;
        }
        executorService = ThreadUtil.newFixedExecutor(processors, 100, "videoTranscoding", false);
    }

    public void convertToM3U8(String fileId, String username, String relativePath, String fileName) {
        executorService.execute(() -> {
            try {
                TimeUnit.SECONDS.sleep(3);
                videoToM3U8(fileId, username, relativePath, fileName, false);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        });
    }

    public void deleteVideoCacheByIds(String username, List<String> fileIds) {
        fileIds.forEach(fileId -> deleteVideoCacheById(username, fileId));
    }

    public void deleteVideoCacheById(String username, String fileId) {
        String videoCacheDir = getVideoCacheDir(username, fileId);
        if (FileUtil.exist(videoCacheDir)) {
            FileUtil.del(videoCacheDir);
        }
    }

    public void deleteVideoCache(String username, String fileAbsolutePath) {
        FileDocument fileDocument = commonFileService.getFileDocument(username, fileAbsolutePath);
        if (fileDocument != null) {
            String fileId = fileDocument.getId();
            String videoCacheDir = getVideoCacheDir(username, fileId);
            if (FileUtil.exist(videoCacheDir)) {
                FileUtil.del(videoCacheDir);
            }
        }
    }

    public VideoInfo getVideoInfo(File videoFile) {
        VideoInfo videoInfo = new VideoInfo();
        if (hasNoFFmpeg()) {
            return videoInfo;
        }
        if (!videoFile.exists()) {
            return videoInfo;
        }
        videoInfo = getVideoInfo(videoFile.getAbsolutePath());
        return videoInfo;
    }

    public VideoInfo getVideoCover(String fileId, String username, String relativePath, String fileName) {
        VideoInfo videoInfo = new VideoInfo();
        if (hasNoFFmpeg()) {
            return videoInfo;
        }
        Path prePath = Paths.get(username, relativePath, fileName);
        String ossPath = CaffeineUtil.getOssPath(prePath);
        Path fileAbsolutePath = Paths.get(fileProperties.getRootDir(), username, relativePath, fileName);
        String videoCacheDir = getVideoCacheDir(username, "");
        // 判断fileId是否为path, 如果为path则取最后一个
        if (fileId.contains("/")) {
            fileId = fileId.substring(fileId.lastIndexOf("/") + 1);
        }
        String outputPath = Paths.get(videoCacheDir, fileId + ".png").toString();
        videoInfo.setCovertPath(outputPath);
        if (FileUtil.exist(outputPath)) {
            return videoInfo;
        }
        try {
            String videoPath = fileAbsolutePath.toString();
            if (ossPath != null) {
                IOssService ossService = OssConfigService.getOssStorageService(ossPath);
                String objectName = WebOssService.getObjectName(prePath, ossPath, false);
                URL url = ossService.getPresignedObjectUrl(objectName, 60);
                if (url != null) {
                    videoPath = url.toString();
                }
            }
            if (FileUtil.exist(outputPath)) {
                return videoInfo;
            }
            videoInfo = getVideoInfo(videoPath);
            videoInfo.setCovertPath(outputPath);
            int videoDuration = videoInfo.getDuration();
            ProcessBuilder processBuilder = getVideoCoverProcessBuilder(videoPath, outputPath, videoDuration);
            printSuccessInfo(processBuilder);
            // 等待处理结果
            outputPath = getWaitingForResults(outputPath, processBuilder);
            if (outputPath != null) {
                return videoInfo;
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            return videoInfo;
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return videoInfo;
    }

    /**
     * 获取视频封面
     * @param videoPath 视频路径
     * @param outputPath 输出路径
     * @param videoDuration 视频时长
     * @return ProcessBuilder
     */
    private static ProcessBuilder getVideoCoverProcessBuilder(String videoPath, String outputPath, int videoDuration) {
        int targetTimestamp = (int) (videoDuration * 0.1);
        String formattedTimestamp = VideoInfoUtil.formatTimestamp(targetTimestamp);
        log.debug("\r\nvideoPath: {}, formattedTimestamp: {}", videoPath, formattedTimestamp);
        ProcessBuilder processBuilder = new ProcessBuilder(
                Constants.FFMPEG,
                "-y",
                "-ss", formattedTimestamp,
                "-i", videoPath,
                "-vf", "scale=320:-2",
                "-frames:v", "1",
                outputPath
        );
        processBuilder.redirectErrorStream(true);
        return processBuilder;
    }

    /**
     * 获取视频文件缓存目录
     *
     * @param username username
     * @param fileId   fileId
     * @return 视频文件缓存目录
     */
    private String getVideoCacheDir(String username, String fileId) {
        // 视频文件缓存目录
        String videoCacheDir = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, fileProperties.getVideoTranscodeCache(), fileId).toString();
        if (!FileUtil.exist(videoCacheDir)) {
            FileUtil.mkdir(videoCacheDir);
        }
        return videoCacheDir;
    }

    private void videoToM3U8(String fileId, String username, String relativePath, String fileName, boolean onlyCPU) throws IOException, InterruptedException {
        Path fileAbsolutePath = Paths.get(fileProperties.getRootDir(), username, relativePath, fileName);
        // 视频文件缓存目录
        String videoCacheDir = getVideoCacheDir(username, fileId);
        if (hasNoFFmpeg()) {
            return;
        }
        String outputPath = Paths.get(videoCacheDir, fileId + ".m3u8").toString();
        if (FileUtil.exist(outputPath)) {
            return;
        }
        // 获取原始视频的分辨率和码率信息
        VideoInfo videoInfo = getVideoInfo(fileAbsolutePath.toString());
        // 判断是否需要转码
        if (!needTranscode(videoInfo)) {
            return;
        }
        // 如果视频的码率小于2500kbps，则使用视频的原始码率
        // 标清视频码率
        int SD_VIDEO_BITRATE = 2500 * 1000;
        int bitrate = SD_VIDEO_BITRATE;
        if (videoInfo.getBitrate() < SD_VIDEO_BITRATE && videoInfo.getBitrate() > 0) {
            bitrate = videoInfo.getBitrate();
        }
        ProcessBuilder processBuilder = cpuTranscoding(fileId, fileAbsolutePath, bitrate, videoCacheDir, outputPath);
        if (!onlyCPU && checkNvidiaDrive()) {
            log.info("use NVENC hardware acceleration");
            processBuilder = useNvencCuda(fileId, fileAbsolutePath, bitrate, videoCacheDir, outputPath);
        }
        if (!onlyCPU && checkMacAppleSilicon()) {
            log.info("use videotoolbox hardware acceleration");
            processBuilder = useVideotoolbox(fileId, fileAbsolutePath, bitrate, videoCacheDir, outputPath);
        }
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        boolean pushMessage = false;
        // 第一个ts文件
        String firstTS = fileId + "-001.ts";
        try (InputStream inputStream = process.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            // 读取命令的输出信息
            String line;
            while ((line = reader.readLine()) != null) {
                // 处理命令的输出信息，例如打印到控制台
                if (line.contains("Error")) {
                    log.error(line);
                }
                if (line.contains(firstTS)) {
                    // 开始转码
                    // log.info("开始转码: {}", fileName);
                    startConvert(username, relativePath, fileName, fileId);
                    pushMessage = true;
                }
                transcodingProgress(fileAbsolutePath, videoInfo.getDuration(), line);
            }
        } finally {
            taskProgressService.removeTaskProgress(fileAbsolutePath.toFile());
        }
        int exitCode = process.waitFor();
        if (exitCode == 0) {
            printSuccessInfo(processBuilder);
            log.info("转码成功: {}, onlyCPU: {}", fileName, onlyCPU);
            if (BooleanUtil.isFalse(pushMessage)) {
                startConvert(username, relativePath, fileName, fileId);
            }
        } else {
            if (!onlyCPU) {
                videoToM3U8(fileId, username, relativePath, fileName, true);
            }
            printErrorInfo(processBuilder, process);
        }
    }

    private static ProcessBuilder useVideotoolbox(String fileId, Path fileAbsolutePath, int bitrate, String videoCacheDir, String outputPath) {
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
                "-vf", "scale=-2:720",
                "-b:v", Convert.toStr(bitrate),
                "-preset", "medium",
                "-g", "48",
                "-sc_threshold", "0",
                "-f", "hls",
                "-hls_segment_filename", Paths.get(videoCacheDir, fileId + "-%03d.ts").toString(),
                outputPath
        );
    }

    private static ProcessBuilder cpuTranscoding(String fileId, Path fileAbsolutePath, int bitrate, String videoCacheDir, String outputPath) {
        return new ProcessBuilder(
                Constants.FFMPEG,
                "-i", fileAbsolutePath.toString(),
                "-profile:v", "main",
                "-pix_fmt", "yuv420p",
                "-level", "4.0",
                "-start_number", "0",
                "-hls_time", "10",
                "-hls_list_size", "0",
                "-vf", "scale=-2:720",
                "-b:v", Convert.toStr(bitrate),
                "-preset", "medium",
                "-g", "48",
                "-sc_threshold", "0",
                "-f", "hls",
                "-hls_segment_filename", Paths.get(videoCacheDir, fileId + "-%03d.ts").toString(),
                outputPath
        );
    }

    private ProcessBuilder useNvencCuda(String fileId, Path fileAbsolutePath, int bitrate, String videoCacheDir, String outputPath) {
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
                "-vf", "setparams=color_primaries=bt709:color_trc=bt709:colorspace=bt709,scale_cuda=-2:720:format=yuv420p",
                "-codec:a:0", "copy",
                "-copyts",
                "-avoid_negative_ts", "disabled",
                "-max_muxing_queue_size", "2048",
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

    /**
     * 检测是否装有Mac Apple Silicon
     */
    private boolean checkMacAppleSilicon() {
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
    private boolean checkNvidiaDrive() {
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
     * 判断是否需要转码
     *
     * @param videoInfo 视频信息
     * @return 是否需要转码
     */
    private boolean needTranscode(VideoInfo videoInfo) {
        if ((videoInfo.getBitrate() > 0 && videoInfo.getBitrate() <= 2000) || videoInfo.getHeight() <= 720) {
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
    private boolean isSupportedFormat(String format) {
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
     * 解析转码进度 0
     *
     * @param fileAbsolutePath 视频文件绝对路径
     * @param videoDuration    视频时长
     * @param line             命令输出信息
     */
    private void transcodingProgress(Path fileAbsolutePath, int videoDuration, String line) {
        // 解析转码进度
        if (line.contains("time=")) {
            try {
                if (line.contains(":")) {
                    String progressStr = getProgressStr(videoDuration, line);
                    log.info("{}, 转码进度: {}%", fileAbsolutePath.getFileName(), progressStr);
                    taskProgressService.addTaskProgress(fileAbsolutePath.toFile(), TaskType.TRANSCODE_VIDEO, progressStr + "%");
                }
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
            }
        }
    }

    /**
     * 获取转码进度
     * @param videoDuration 视频时长
     * @param line         命令输出信息
     * @return 转码进度
     */
    private static String getProgressStr(int videoDuration, String line) {
        String[] parts = line.split("time=")[1].split(" ")[0].split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);
        int seconds = Integer.parseInt(parts[2].split("\\.")[0]);
        int totalSeconds = hours * 3600 + minutes * 60 + seconds;
        // 计算转码进度百分比
        double progress = (double) totalSeconds / videoDuration * 100;
        return String.format("%.2f", progress);
    }

    private void startConvert(String username, String relativePath, String fileName, String fileId) {
        Query query = new Query();
        String userId = userService.getUserIdByUserName(username);
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("path").is(relativePath));
        query.addCriteria(Criteria.where("name").is(fileName));
        Update update = new Update();
        String m3u8 = Paths.get(username, fileId + ".m3u8").toString();
        update.set("m3u8", m3u8);
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class);
        if (fileDocument == null) {
            return;
        }
        mongoTemplate.upsert(query, update, FileDocument.class);
        fileDocument.setM3u8(m3u8);
        commonFileService.pushMessage(username, fileDocument, Constants.UPDATE_FILE);
    }

    /**
     * 获取视频的分辨率和码率信息
     *
     * @param videoPath 视频路径
     * @return 视频信息
     */
    private VideoInfo getVideoInfo(String videoPath) {
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
                    int bitrate = streamObject.getIntValue("bit_rate"); // bps
                    return new VideoInfo(videoPath, width, height, format, bitrate, duration);
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

    @PreDestroy
    private void destroy() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }

}

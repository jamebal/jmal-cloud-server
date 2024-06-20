package com.jmal.clouddisk.video;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.ObjectUtil;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

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

    /**
     * 视频转码线程池
     */
    private ExecutorService videoTranscodingService;

    /**
     * 添加转码任务线程池
     */
    private ExecutorService addTranscodingTaskService;

    /**
     * 处理待转码文件锁, 防止多次处理
     */
    private final ReentrantLock toBeTranscodeLock = new ReentrantLock();

    private final static String TRANSCODE_VIDEO  = "transcodeVideo";

    @PostConstruct
    public void init() {
        getVideoTranscodingService();
        addTranscodingTaskService = ThreadUtil.newFixedExecutor(8, 100, "addTranscodingTask", true);
    }

    private void getVideoTranscodingService() {
        TranscodeConfig transcodeConfig = getTranscodeConfig();
        int processors = 1;
        if (transcodeConfig.getMaxThreads() != null) {
            processors = transcodeConfig.getMaxThreads();
        }
        synchronized (this) {
            if (videoTranscodingService == null) {
                createExecutor(processors);
                return;
            }
        }
        if (videoTranscodingService.isShutdown()) {
            createExecutor(processors);
        } else {
            try {
                videoTranscodingService.shutdown();
                if (!videoTranscodingService.awaitTermination(30, TimeUnit.MINUTES)) {
                    log.warn("等待转码超时, 尝试强制停止所有转码任务");
                    videoTranscodingService.shutdownNow();
                }
            } catch (InterruptedException e) {
                videoTranscodingService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            createExecutor(processors);
        }
    }

    private void createExecutor(int processors) {
        videoTranscodingService = ThreadUtil.newFixedExecutor(processors, 1, "videoTranscoding", true);
    }

    /**
     * 设置转码配置
     * @param config TranscodeConfig
     */
    public void setTranscodeConfig(TranscodeConfig config) {
        if (config == null) {
            return;
        }
        Query query = new Query();
        TranscodeConfig tc = mongoTemplate.findOne(query, TranscodeConfig.class);
        if (tc == null) {
            mongoTemplate.save(config);
        } else {
            Update update = new Update();
            update.set("enable", config.getEnable());
            update.set("bitrate", config.getBitrate());
            update.set("height", config.getHeight());
            mongoTemplate.updateFirst(query, update, TranscodeConfig.class);
            if (ObjectUtil.equals(tc.getEnable(), config.getEnable())) {
                // 重新加载转码线程池
                addTranscodingTaskService.execute(this::getVideoTranscodingService);
            }
        }
    }

    public TranscodeConfig getTranscodeConfig() {
        Query query = new Query();
        TranscodeConfig config = mongoTemplate.findOne(query, TranscodeConfig.class);
        if (config == null) {
            config = new TranscodeConfig();
        }
        return config;
    }

    /**
     * 设置转码状态
     * @param fileId fileId
     * @param transcodeStatus TranscodeStatus
     */
    private void updateTranscodeVideo(String fileId, TranscodeStatus transcodeStatus) {
        // 设置未转码标记
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update();
        if (transcodeStatus == TranscodeStatus.TRANSCENDED) {
            update.unset(TRANSCODE_VIDEO);
        } else {
            update.set(TRANSCODE_VIDEO, transcodeStatus.getStatus());
        }
        mongoTemplate.updateFirst(query, update, CommonFileService.COLLECTION_NAME);
    }

    /**
     * 处理待转码文件
     */
    public void processingToBeTranscode() {
        boolean run = true;
        log.debug("开始处理待转码文件");
        while (run) {
            Query query = new Query();
            query.addCriteria(Criteria.where(TRANSCODE_VIDEO).is(TranscodeStatus.NOT_TRANSCODE.getStatus()));
            long count = mongoTemplate.count(query, CommonFileService.COLLECTION_NAME);
            if (count == 0) {
                log.debug("待转码文件处理完成");
                run = false;
            }
            query.limit(8);
            List<FileDocument> fileDocumentList = mongoTemplate.find(query, FileDocument.class, CommonFileService.COLLECTION_NAME);
            for (FileDocument fileDocument : fileDocumentList) {
                String fileId = fileDocument.getId();
                String username = userService.getUserNameById(fileDocument.getUserId());
                String relativePath = fileDocument.getPath();
                String fileName = fileDocument.getName();
                updateTranscodeVideo(fileId, TranscodeStatus.TRANSCODING);
                videoTranscodingService.execute(() -> doConvertToM3U8(fileId, username, relativePath, fileName));
            }
        }
    }

    private void startProcessFilesToBeIndexed() {
        addTranscodingTaskService.execute(() -> {
            if (!toBeTranscodeLock.tryLock()) {
                return;
            }
            try {
                processingToBeTranscode();
            } finally {
                toBeTranscodeLock.unlock();
            }
        });
    }

    public void convertToM3U8(String fileId) {
        addTranscodingTaskService.execute(() -> {
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
            }
            updateTranscodeVideo(fileId, TranscodeStatus.NOT_TRANSCODE);
            startProcessFilesToBeIndexed();
        });
    }

    /**
     * 转码视频
     * @param fileId fileId
     * @param username username
     * @param relativePath relativePath
     * @param fileName fileName
     */
    private void doConvertToM3U8(String fileId, String username, String relativePath, String fileName) {
        try {
            videoToM3U8(fileId, username, relativePath, fileName, false);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } finally {
            updateTranscodeVideo(fileId, TranscodeStatus.TRANSCENDED);
        }
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
        if (FFMPEGCommand.hasNoFFmpeg()) {
            return videoInfo;
        }
        if (!videoFile.exists()) {
            return videoInfo;
        }
        videoInfo = FFMPEGCommand.getVideoInfo(videoFile.getAbsolutePath());
        return videoInfo;
    }

    public VideoInfo getVideoCover(String fileId, String username, String relativePath, String fileName) {
        VideoInfo videoInfo = new VideoInfo();
        if (FFMPEGCommand.hasNoFFmpeg()) {
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
            videoInfo = FFMPEGCommand.getVideoInfo(videoPath);
            videoInfo.setCovertPath(outputPath);
            int videoDuration = videoInfo.getDuration();
            ProcessBuilder processBuilder = FFMPEGCommand.getVideoCoverProcessBuilder(videoPath, outputPath, videoDuration);
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
        TranscodeConfig transcodeConfig = getTranscodeConfig();
        if (BooleanUtil.isFalse(transcodeConfig.getEnable())) {
            return;
        }
        Path fileAbsolutePath = Paths.get(fileProperties.getRootDir(), username, relativePath, fileName);
        // 视频文件缓存目录
        String videoCacheDir = getVideoCacheDir(username, fileId);
        if (FFMPEGCommand.hasNoFFmpeg()) {
            return;
        }
        String outputPath = Paths.get(videoCacheDir, fileId + ".m3u8").toString();
        if (FileUtil.exist(outputPath)) {
            return;
        }
        // 获取原始视频的分辨率和码率信息
        VideoInfo videoInfo = FFMPEGCommand.getVideoInfo(fileAbsolutePath.toString());
        // 判断是否需要转码
        if (!FFMPEGUtils.needTranscode(videoInfo, transcodeConfig)) {
            return;
        }
        // 如果视频的码率小于配置码率，则使用视频的原始码率
        // 标清视频码率
        int SD_VIDEO_BITRATE = transcodeConfig.getBitrate() * 1000;
        int bitrate = SD_VIDEO_BITRATE;
        if (videoInfo.getBitrate() < SD_VIDEO_BITRATE && videoInfo.getBitrate() > 0) {
            bitrate = videoInfo.getBitrate();
        }
        int targetHeight = transcodeConfig.getHeight();
        if (videoInfo.getHeight() < targetHeight) {
            targetHeight = videoInfo.getHeight();
        }

        // 计算缩略图间隔
        int vttInterval = FFMPEGUtils.getVttInterval(videoInfo);
        Path vttPath = Paths.get(videoCacheDir, "vtt");
        if (!Files.exists(vttPath)) {
            FileUtil.mkdir(vttPath);
        }
        String thumbnailPattern = Paths.get(vttPath.toString(), "thumb_%03d.png").toString();

        ProcessBuilder processBuilder = FFMPEGCommand.cpuTranscoding(fileId, fileAbsolutePath, bitrate, targetHeight, videoCacheDir, outputPath, vttInterval, thumbnailPattern);
        if (!onlyCPU && FFMPEGCommand.checkNvidiaDrive()) {
            log.info("use NVENC hardware acceleration");
            processBuilder = FFMPEGCommand.useNvencCuda(fileId, fileAbsolutePath, bitrate, targetHeight, videoCacheDir, outputPath, vttInterval, thumbnailPattern);
        }
        if (!onlyCPU && FFMPEGCommand.checkMacAppleSilicon()) {
            log.info("use videotoolbox hardware acceleration");
            processBuilder = FFMPEGCommand.useVideotoolbox(fileId, fileAbsolutePath, bitrate, targetHeight, videoCacheDir, outputPath, vttInterval, thumbnailPattern);
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
            // 生成vtt缩略图
            generateVtt(fileId, videoCacheDir, videoInfo, vttInterval, thumbnailPattern);
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

    private static void generateVtt(String fileId, String videoCacheDir, VideoInfo videoInfo, int vttInterval, String thumbnailPattern) throws InterruptedException, IOException {
        String vttFilePath = Paths.get(videoCacheDir, fileId + ".vtt").toString();
        int columns = 10; // 合并图像的列数
        int rows = (int) Math.ceil((double) videoInfo.getDuration() / vttInterval / columns);
        String thumbnailImagePath = Paths.get(videoCacheDir, fileId + "-vtt.jpg").toString();
        ProcessBuilder processBuilder = FFMPEGCommand.mergeVTT(thumbnailPattern, thumbnailImagePath, columns, rows);
        processBuilder.start().waitFor();
        FFMPEGUtils.generateVTT(videoInfo, vttInterval, vttFilePath, String.format("%s-vtt.jpg", fileId), columns);
        // 删除vtt临时文件
        Path vttPath = Paths.get(videoCacheDir, "vtt");
        FileUtil.del(vttPath);
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
                    String progressStr = FFMPEGUtils.getProgressStr(videoDuration, line);
                    log.info("{}, 转码进度: {}%", fileAbsolutePath.getFileName(), progressStr);
                    taskProgressService.addTaskProgress(fileAbsolutePath.toFile(), TaskType.TRANSCODE_VIDEO, progressStr + "%");
                }
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
            }
        }
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

    @PreDestroy
    private void destroy() {
        if (videoTranscodingService != null) {
            videoTranscodingService.shutdown();
        }
        if (addTranscodingTaskService != null) {
            addTranscodingTaskService.shutdown();
        }
    }

}

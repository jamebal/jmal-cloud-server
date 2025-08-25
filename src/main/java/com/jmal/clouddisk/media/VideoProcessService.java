package com.jmal.clouddisk.media;

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
import com.jmal.clouddisk.service.impl.CommonUserService;
import com.jmal.clouddisk.service.impl.MessageService;
import com.jmal.clouddisk.service.impl.PathService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.result.UpdateResult;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.ContextRefreshedEvent;
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
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static com.jmal.clouddisk.util.FFMPEGUtils.*;

@Service
@Lazy
@Slf4j
@RequiredArgsConstructor
public class VideoProcessService implements ApplicationListener<ContextRefreshedEvent> {

    private final FileProperties fileProperties;

    private final CommonUserService userService;

    private final PathService pathService;

    private final MessageService messageService;

    private final TaskProgressService taskProgressService;

    private final MongoTemplate mongoTemplate;

    /**
     * 视频转码线程池
     */
    private ExecutorService videoTranscodingService;

    /**
     * 视频转码任务
     */
    private final static Map<String, Future<?>> videoTranscodingTasks = new ConcurrentHashMap<>();
    private final static Set<Process> processesTasks = new CopyOnWriteArraySet<>();

    /**
     * 添加转码任务线程池
     */
    private ExecutorService addTranscodingTaskService;

    /**
     * 正在转码的视频文件数
     */
    private final static AtomicInteger transcodingCount = new AtomicInteger(0);

    /**
     * 等待转码的视频文件数
     */
    private final static AtomicInteger waitingTranscodingCount = new AtomicInteger(0);

    /**
     * 处理待转码文件锁, 防止多次处理
     */
    private final ReentrantLock toBeTranscodeLock = new ReentrantLock();

    private final static String TRANSCODE_VIDEO = "transcodeVideo";

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 确保只在根应用上下文执行一次，防止在Web环境中执行两次
        if (event.getApplicationContext().getParent() == null) {
            getVideoTranscodingService();
            addTranscodingTaskService = Executors.newVirtualThreadPerTaskExecutor();
        }
    }

    private void getVideoTranscodingService() {
        TranscodeConfig transcodeConfig = getTranscodeConfig();
        int processors = transcodeConfig.getMaxThreads();
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
                if (!videoTranscodingService.awaitTermination(180, TimeUnit.MINUTES)) {
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
        waitingTranscodingCount.set(0);
        transcodingCount.set(0);
        videoTranscodingService = ThreadUtil.newFixedExecutor(processors, 1, "videoTranscoding", true);
    }

    /**
     * 取消转码任务
     */
    public void cancelTranscodeTask() {
        // 修改所有未转码的视频文件状态
        Query query = new Query();
        query.addCriteria(Criteria.where(TRANSCODE_VIDEO).is(TranscodeStatus.NOT_TRANSCODE.getStatus()));
        Update update = new Update();
        update.unset(TRANSCODE_VIDEO);
        mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
        // 取消process任务
        processesTasks.forEach(p -> {
            if (p.isAlive()) {
                p.destroy();
            }
        });
        processesTasks.clear();

        // 取消转码任务
        videoTranscodingTasks.forEach((_, future) -> {
            if (!future.isDone()) {
                future.cancel(true);
            }
        });
        videoTranscodingTasks.clear();
        videoTranscodingService.shutdownNow();
        getVideoTranscodingService();
        // 等待 waitingTranscodingCount < 0 且 transcodingCount < 0
        while (waitingTranscodingCount.get() > 0  || transcodingCount.get() > 0) {
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
                Thread.currentThread().interrupt();
            }
        }
        // 更新转码状态
        taskProgressService.pushTranscodeStatus(getTranscodeStatus());
    }

    /**
     * 设置转码配置
     *
     * @param config TranscodeConfig
     */
    public long setTranscodeConfig(TranscodeConfig config) {
        if (config == null) {
            return 0;
        }
        Query query = new Query();
        TranscodeConfig tc = mongoTemplate.findOne(query, TranscodeConfig.class);
        if (tc == null) {
            mongoTemplate.save(config);
        } else {
            Update update = getTranscodeConfigUpdate(config);
            mongoTemplate.updateFirst(query, update, TranscodeConfig.class);
            if (!ObjectUtil.equals(tc.getMaxThreads(), config.getMaxThreads())) {
                // 重新加载转码线程池
                addTranscodingTaskService.execute(this::getVideoTranscodingService);
            }
            // 检查是否需要重新转码
            if (BooleanUtil.isTrue(config.getIsReTranscode())) {
                // 检查转码参数是否变化
                return checkTranscodeConfigChange(config);
            }
        }
        return 0;
    }

    /**
     * 检查转码参数是否变化
     *
     * @param config 新参数
     */
    private long checkTranscodeConfigChange(TranscodeConfig config) {
        // 更新所有视频文件的转码状态
        List<String> fileIdList = getTranscodeConfigQuery(config);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(fileIdList));
        Update update = new Update();
        update.set(TRANSCODE_VIDEO, TranscodeStatus.NOT_TRANSCODE.getStatus());
        UpdateResult updateResult = mongoTemplate.updateMulti(query, update, CommonFileService.COLLECTION_NAME);
        if (updateResult.getModifiedCount() > 0) {
            log.info("需要重新转码的数量: {}", updateResult.getModifiedCount());
            createExecutor(config.getMaxThreads());
            // 重新转码
            startProcessFilesToBeIndexed();
            return updateResult.getModifiedCount();
        }
        return 0;
    }

    private List<String> getTranscodeConfigQuery(TranscodeConfig config) {
        List<Bson> pipeline = Arrays.asList(new Document("$match",
                        new Document("video",
                                new Document("$exists", true))),
                new Document("$match",
                        new Document("$or", Arrays.asList(
                                new Document("video.height",
                                        new Document("$exists", false)),
                                new Document("video.height",
                                        new Document("$gt", config.getHeightCond())),
                                new Document("video.bitrateNum",
                                        new Document("$gt", config.getBitrateCond() * 1000)),
                                new Document("video.frameRate",
                                        new Document("$gt", config.getFrameRateCond()))))),
                new Document("$match",
                        new Document("$or", Arrays.asList(new Document("video.toHeight",
                                        new Document("$ne", config.getHeight())),
                                new Document("video.toBitrate",
                                        new Document("$ne", config.getBitrate())),
                                new Document("video.toFrameRate",
                                        new Document("$ne", config.getFrameRate()))))),
                new Document("$project",
                        new Document("_id", 1L)));
        List<String> fileIdList = new ArrayList<>();
        AggregateIterable<Document> aggregateIterable = mongoTemplate.getCollection(CommonFileService.COLLECTION_NAME).aggregate(pipeline);
        for (org.bson.Document document : aggregateIterable) {
            String fileId = document.getObjectId("_id").toHexString();
            fileIdList.add(fileId);
        }
        return fileIdList;
    }

    private static @NotNull Update getTranscodeConfigUpdate(TranscodeConfig config) {
        Update update = new Update();
        update.set("enable", config.getEnable());
        update.set("maxThreads", config.getMaxThreads());
        update.set("bitrate", config.getBitrate());
        update.set("height", config.getHeight());
        update.set("frameRate", config.getFrameRate());
        update.set("bitrateCond", config.getBitrateCond());
        update.set("heightCond", config.getHeightCond());
        update.set("frameRateCond", config.getFrameRateCond());
        update.set("vttThumbnailCount", config.getVttThumbnailCount());
        update.set("isReTranscode", config.getIsReTranscode());
        return update;
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
     *
     * @param fileId          fileId
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

    public Map<String, Integer> getTranscodeStatus() {
        return Map.of("waitingTranscodingCount", waitingTranscodingCount.get(), "transcodingCount", transcodingCount.get());
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
            // 更新待转码文件数量
            waitingTranscodingCount.set((int) count);
            // 更新转码状态
            taskProgressService.pushTranscodeStatus(getTranscodeStatus());
            if (count == 0) {
                log.debug("待转码文件处理完成");
                run = false;
            }
            query.limit(1);
            List<FileDocument> fileDocumentList = mongoTemplate.find(query, FileDocument.class, CommonFileService.COLLECTION_NAME);
            for (FileDocument fileDocument : fileDocumentList) {
                String fileId = fileDocument.getId();
                String username = userService.getUserNameById(fileDocument.getUserId());
                String relativePath = fileDocument.getPath();
                String fileName = fileDocument.getName();
                updateTranscodeVideo(fileId, TranscodeStatus.TRANSCODING);
                Future<?> videoTranscodingTask = videoTranscodingService.submit(() -> doConvertToM3U8(fileId, username, relativePath, fileName));
                videoTranscodingTasks.put(fileId, videoTranscodingTask);
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
     *
     * @param fileId       fileId
     * @param username     username
     * @param relativePath relativePath
     * @param fileName     fileName
     */
    private void doConvertToM3U8(String fileId, String username, String relativePath, String fileName) {
        try {
            // 更新正在转码的视频文件数
            transcodingCount.incrementAndGet();
            waitingTranscodingCount.decrementAndGet();
            // 更新转码状态
            taskProgressService.pushTranscodeStatus(getTranscodeStatus());
            // 开始转码
            videoToM3U8(fileId, username, relativePath, fileName, false);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } finally {
            // 更新正在转码的视频文件数
            transcodingCount.decrementAndGet();
            // 更新转码状态
            taskProgressService.pushTranscodeStatus(getTranscodeStatus());
            updateTranscodeVideo(fileId, TranscodeStatus.TRANSCENDED);
            videoTranscodingTasks.remove(fileId);
        }
    }

    public void deleteVideoCacheByIds(String username, List<String> fileIds) {
        fileIds.forEach(fileId -> deleteVideoCacheById(username, fileId));
    }

    public void deleteVideoCacheById(String username, String fileId) {
        String videoCacheDir = pathService.getVideoCacheDir(username, fileId);
        if (FileUtil.exist(videoCacheDir)) {
            FileUtil.del(videoCacheDir);
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
        String videoCacheDir = pathService.getVideoCacheDir(username, "");
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
            Process process = processBuilder.start();
            outputPath = getWaitingForResults(outputPath, processBuilder, process);
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

    private void videoToM3U8(String fileId, String username, String relativePath, String fileName, boolean onlyCPU) throws IOException, InterruptedException {
        TranscodeConfig transcodeConfig = getTranscodeConfig();
        if (BooleanUtil.isFalse(transcodeConfig.getEnable())) {
            return;
        }
        Path fileAbsolutePath = Paths.get(fileProperties.getRootDir(), username, relativePath, fileName);
        // 视频文件缓存目录
        String videoCacheDir = pathService.getVideoCacheDir(username, fileId);
        if (FFMPEGCommand.hasNoFFmpeg()) {
            return;
        }
        String outputPath = Paths.get(videoCacheDir, fileId + ".m3u8").toString();
        if (FileUtil.exist(outputPath) && !checkTranscodeChange(fileId, transcodeConfig)) {
            return;
        }
        // 获取原始视频的分辨率和码率信息
        VideoInfo videoInfo = FFMPEGCommand.getVideoInfo(fileAbsolutePath.toString());
        // 判断是否需要转码
        if (!FFMPEGUtils.needTranscode(videoInfo, transcodeConfig)) {
            return;
        }
        // 如果视频的码率小于配置码率，则使用视频的原始码率
        int SD_VIDEO_BITRATE = transcodeConfig.getBitrate() * 1000;
        int bitrate = SD_VIDEO_BITRATE;
        if (videoInfo.getBitrate() < SD_VIDEO_BITRATE && videoInfo.getBitrate() > 0) {
            bitrate = videoInfo.getBitrate();
        }
        // 如果视频的高度小于配置高度，则使用视频的原始高度
        int targetHeight = transcodeConfig.getHeight();
        if (videoInfo.getHeight() < targetHeight) {
            targetHeight = videoInfo.getHeight();
        }
        // 如果视频的帧率小于配置帧率，则使用视频的原始帧率
        double frameRate = transcodeConfig.getFrameRate();
        if (videoInfo.getFrameRate() < frameRate) {
            frameRate = videoInfo.getFrameRate();
        }

        // 计算缩略图间隔
        int vttInterval = FFMPEGUtils.getVttInterval(videoInfo, transcodeConfig.getVttThumbnailCount());
        Path vttPath = Paths.get(videoCacheDir, "vtt");
        if (!Files.exists(vttPath)) {
            FileUtil.mkdir(vttPath);
        }
        String thumbnailPattern = Paths.get(vttPath.toString(), "thumb_%03d.png").toString();

        ProcessBuilder processBuilder = FFMPEGCommand.cpuTranscoding(fileId, fileAbsolutePath, bitrate, targetHeight, videoCacheDir, outputPath, vttInterval, thumbnailPattern, frameRate);
        if (!onlyCPU && FFMPEGCommand.checkNvidiaDrive()) {
            log.info("use NVENC hardware acceleration");
            processBuilder = FFMPEGCommand.useNvencCuda(fileId, fileAbsolutePath, bitrate, targetHeight, videoCacheDir, outputPath, frameRate);
            generateVttOfNvidia(fileAbsolutePath, vttInterval, thumbnailPattern, videoInfo);
        }
        if (!onlyCPU && FFMPEGCommand.checkMacAppleSilicon()) {
            log.info("use videotoolbox hardware acceleration");
            processBuilder = FFMPEGCommand.useVideotoolbox(fileId, fileAbsolutePath, bitrate, targetHeight, videoCacheDir, outputPath, vttInterval, thumbnailPattern, frameRate);
        }
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        processesTasks.add(process);
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
                    startConvert(username, relativePath, fileName, fileId, transcodeConfig);
                    pushMessage = true;
                }
                transcodingProgress(fileAbsolutePath, videoInfo.getDuration(), line, "");
            }

            // 生成vtt缩略图
            generateVtt(fileId, videoCacheDir, videoInfo, vttInterval, thumbnailPattern);

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                printSuccessInfo(processBuilder);
                log.info("转码成功: {}, onlyCPU: {}", fileName, onlyCPU);
                if (BooleanUtil.isFalse(pushMessage)) {
                    startConvert(username, relativePath, fileName, fileId, transcodeConfig);
                }
            } else {
                if (!onlyCPU) {
                    videoToM3U8(fileId, username, relativePath, fileName, true);
                }
                printErrorInfo(processBuilder, process);
            }

        } catch (InterruptedException exception) {
            log.warn("转码被中断: {}", fileName);
            Thread.currentThread().interrupt();
        } finally {
            taskProgressService.removeTaskProgress(fileAbsolutePath.toFile());
            processesTasks.remove(process);
        }
    }

    private void generateVttOfNvidia(Path fileAbsolutePath, int vttInterval, String thumbnailPattern, VideoInfo videoInfo) throws IOException {
        // 生成vtt缩略图, nvidia加速时要单独生成vtt缩略图
        taskProgressService.addTaskProgress(fileAbsolutePath.toFile(), TaskType.TRANSCODE_VIDEO, "vtt生成中...");
        ProcessBuilder processBuilder = FFMPEGCommand.useNvencCudaVtt(fileAbsolutePath, vttInterval, thumbnailPattern);
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        processesTasks.add(process);
        // 第一个ts文件
        try (InputStream inputStream = process.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            // 读取命令的输出信息
            String line;
            while ((line = reader.readLine()) != null) {
                // 处理命令的输出信息，例如打印到控制台
                if (line.contains("Error")) {
                    log.error(line);
                }
                transcodingProgress(fileAbsolutePath, videoInfo.getDuration(), line, "vtt—");
            }
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                printSuccessInfo(processBuilder);
            } else {
                printErrorInfo(processBuilder, process);
            }
        } catch (InterruptedException exception) {
            log.warn("生成vtt缩略图被中断: {}", fileAbsolutePath.getFileName());
            Thread.currentThread().interrupt();
        } finally {
            processesTasks.remove(process);
        }
    }

    /**
     * 检查转码参数是否变化
     *
     * @param fileId 文件id
     * @param config 转码配置
     * @return 是否变化
     */
    private boolean checkTranscodeChange(String fileId, TranscodeConfig config) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class);
        if (fileDocument == null) {
            return true;
        }
        VideoInfoDO videoInfo = fileDocument.getVideo();
        if (videoInfo == null) {
            return true;
        }
        return !Objects.equals(videoInfo.getToHeight(), config.getHeight()) || !Objects.equals(videoInfo.getToBitrate(), config.getBitrate()) || !Objects.equals(videoInfo.getToFrameRate(), config.getFrameRate());
    }

    private void generateVtt(String fileId, String videoCacheDir, VideoInfo videoInfo, int vttInterval, String thumbnailPattern) throws InterruptedException, IOException {
        String vttFilePath = Paths.get(videoCacheDir, fileId + ".vtt").toString();
        int columns = 10; // 合并图像的列数
        int rows = (int) Math.ceil((double) videoInfo.getDuration() / vttInterval / columns);
        String thumbnailImagePath = Paths.get(videoCacheDir, fileId + "-vtt.jpg").toString();
        ProcessBuilder processBuilder = FFMPEGCommand.mergeVTT(thumbnailPattern, thumbnailImagePath, columns, rows);
        // 等待处理结果
        Process process = processBuilder.start();
        processesTasks.add(process);
        try {
            String outputPath = getWaitingForResults(thumbnailImagePath, processBuilder, process);
            if (outputPath != null) {
                log.info("生成vtt缩略图成功: {}", fileId);
                printSuccessInfo(processBuilder);
                FFMPEGUtils.generateVTT(videoInfo, vttInterval, vttFilePath, String.format("%s-vtt.jpg", fileId), columns);
                // 删除vtt临时文件
                Path vttPath = Paths.get(videoCacheDir, "vtt");
                FileUtil.del(vttPath);
            } else {
                log.error("生成vtt缩略图失败: {}", fileId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            processesTasks.remove(process);
        }
    }

    /**
     * 解析转码进度 0
     *
     * @param fileAbsolutePath 视频文件绝对路径
     * @param videoDuration    视频时长
     * @param line             命令输出信息
     */
    private void transcodingProgress(Path fileAbsolutePath, int videoDuration, String line, String desc) {
        // 解析转码进度
        if (line.contains("time=")) {
            try {
                if (line.contains(":")) {
                    double progress = FFMPEGUtils.getProgressStr(videoDuration, line);
                    log.debug("{}, 转码进度: {}%", fileAbsolutePath.getFileName(), progress);
                    taskProgressService.addTaskProgress(fileAbsolutePath.toFile(), TaskType.TRANSCODE_VIDEO, desc + progress + "%");
                }
            } catch (Exception e) {
                log.warn(e.getMessage(), e);
            }
        }
    }

    private void startConvert(String username, String relativePath, String fileName, String fileId, TranscodeConfig transcodeConfig) {
        Query query = new Query();
        String userId = userService.getUserIdByUserName(username);
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("path").is(relativePath));
        query.addCriteria(Criteria.where("name").is(fileName));
        Update update = new Update();
        String m3u8 = Paths.get(username, fileId + ".m3u8").toString();
        update.set("m3u8", m3u8);
        String vtt = Paths.get(username, fileId + ".vtt").toString();
        update.set("vtt", vtt);

        update.set("video.toHeight", transcodeConfig.getHeight());
        update.set("video.toBitrate", transcodeConfig.getBitrate());
        update.set("video.toFrameRate", transcodeConfig.getFrameRate());

        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class);
        if (fileDocument == null) {
            return;
        }
        mongoTemplate.upsert(query, update, FileDocument.class);
        fileDocument.setM3u8(m3u8);
        messageService.pushMessage(username, fileDocument, Constants.UPDATE_FILE);
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

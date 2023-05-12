package com.jmal.clouddisk.service.video;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.crypto.SecureUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.service.IUserService;
import com.jmal.clouddisk.service.impl.CommonFileService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;

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

    @Resource
    MongoTemplate mongoTemplate;

    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        int processors = Runtime.getRuntime().availableProcessors() - 1;
        executorService = ThreadUtil.newFixedExecutor(processors, 100, "videoTranscoding", false);
    }

    public void convertToM3U8(String username, String relativePath, String fileName) {
        executorService.execute(() -> {
            try {
                videoToM3U8(username, relativePath, fileName);
            } catch (InterruptedException e) {
                log.error(e.getMessage(), e);
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        });
    }

    public void deleteVideoCache(String username, String relativePath, String fileName) {
        Path fileAbsolutePath = Paths.get(fileProperties.getRootDir(), username, relativePath, fileName);
        String fileMd5 = SecureUtil.md5(fileAbsolutePath.toString());
        String videoCacheDir = getVideoCacheDir(username, fileMd5);
        if (FileUtil.exist(videoCacheDir)) {
            FileUtil.del(videoCacheDir);
        }
    }

    public String getVideoCover(String username, String relativePath, String fileName) {
        if (hasNoFFmpeg()) {
            return null;
        }
        Path fileAbsolutePath = Paths.get(fileProperties.getRootDir(), username, relativePath, fileName);
        String fileMd5 = SecureUtil.md5(fileAbsolutePath.toString());
        String videoCacheDir = getVideoCacheDir(username, fileMd5);
        String outputPath = Paths.get(videoCacheDir, fileMd5 + ".png").toString();
        if (FileUtil.exist(outputPath)) {
            return outputPath;
        }
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "ffmpeg",
                    "-i", fileAbsolutePath.toString(),
                    "-vf", "thumbnail,scale=320:180",
                    "-frames:v", "1",
                    outputPath
            );
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                if (FileUtil.exist(outputPath)) {
                    return outputPath;
                }
            } else {
                printErrorInfo(processBuilder);
            }
            return null;
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
        return null;
    }

    /**
     * 获取视频文件缓存目录
     *
     * @param username username
     * @param fileMd5  视频文件MD5
     * @return 视频文件缓存目录
     */
    private String getVideoCacheDir(String username, String fileMd5) {
        // 视频文件缓存目录
        String videoCacheDir = Paths.get(fileProperties.getRootDir(), fileProperties.getChunkFileDir(), username, fileProperties.getVideoTranscodeCache(), fileMd5).toString();
        if (!FileUtil.exist(videoCacheDir)) {
            FileUtil.mkdir(videoCacheDir);
        }
        return videoCacheDir;
    }

    private void videoToM3U8(String username, String relativePath, String fileName) throws IOException, InterruptedException {
        Path fileAbsolutePath = Paths.get(fileProperties.getRootDir(), username, relativePath, fileName);
        String fileType = FileTypeUtil.getType(fileAbsolutePath.toFile());
        if (!fileType.equals("avi")) {
            return;
        }
        String fileMd5 = SecureUtil.md5(fileAbsolutePath.toString());
        // 视频文件缓存目录
        String videoCacheDir = getVideoCacheDir(username, fileMd5);
        if (hasNoFFmpeg()) {
            return;
        }
        String outputPath = Paths.get(videoCacheDir, fileMd5 + ".m3u8").toString();
        if (FileUtil.exist(outputPath)) {
            return;
        }
        ProcessBuilder processBuilder = new ProcessBuilder(
                "ffmpeg",
                "-i", fileAbsolutePath.toString(),
                "-profile:v", "baseline",
                "-level", "3.0",
                "-start_number", "0",
                "-hls_time", "10",
                "-hls_list_size", "0",
                "-f", "hls",
                "-hls_segment_filename", Paths.get(videoCacheDir, fileMd5 + "-%03d.ts").toString(),
                outputPath
        );
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();
        boolean pushMessage = false;
        // 第一个ts文件
        String firstTS = fileMd5 + "-003.ts";
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
                    log.info("开始转码: {}", fileName);
                    startConvert(username, relativePath, fileName, fileMd5);
                    pushMessage = true;
                }
            }
        }
        int exitCode = process.waitFor();
        if (exitCode == 0) {
            log.info("转码成功: {}", fileName);
            if (BooleanUtil.isFalse(pushMessage)) {
                startConvert(username, relativePath, fileName, fileMd5);
            }
        } else {
            printErrorInfo(processBuilder);
        }
    }

    private void startConvert(String username, String relativePath, String fileName, String fileMd5) {
        Query query = new Query();
        String userId = userService.getUserIdByUserName(username);
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        query.addCriteria(Criteria.where("path").is(relativePath));
        query.addCriteria(Criteria.where("name").is(fileName));
        Update update = new Update();
        String m3u8 = Paths.get(username, fileMd5 + ".m3u8").toString();
        update.set("m3u8", m3u8);
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class);
        if (fileDocument == null) {
            return;
        }
        mongoTemplate.upsert(query, update, FileDocument.class);
        fileDocument.setM3u8(m3u8);
        commonFileService.pushMessage(username, fileDocument, "updateFile");
    }

    private static void printErrorInfo(ProcessBuilder processBuilder) {
        log.error("ffmpeg 执行失败");
        processBuilder.command().forEach(command -> Console.log(command + " \\"));
    }

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
            e.printStackTrace();
        }
        return true;
    }

}

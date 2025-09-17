package com.jmal.clouddisk.ocr;

import cn.hutool.core.text.CharSequenceUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jmal.clouddisk.dao.IOcrConfigDAO;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.lucene.TaskProgressService;
import com.jmal.clouddisk.lucene.TaskType;
import com.jmal.clouddisk.media.ImageMagickProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {

    private final Map<String, IOcrService> ocrServiceMap;

    private final ImageMagickProcessor imageMagickProcessor;

    private final TaskProgressService taskProgressService;

    private final IOcrConfigDAO ocrConfigDAO;

    // 初始设置为1个并发请求
    private final Semaphore semaphore = new Semaphore(1);

    private final Cache<String, OcrConfig> ocrConfigCache = Caffeine.newBuilder().build();

    /**
     * 提取PDF页面并使用OCR识别
     * @param pageIndex 页码
     * @param imageForOcr 渲染后的图片路径
     * @param totalPages 总页数
     * @param username 用户名
     */
    public void extractPageWithOCR(Writer writer, File file, String imageForOcr, int pageIndex, int totalPages, String username) {
        try {

            taskProgressService.addTaskProgress(file, TaskType.OCR, pageIndex + 1 + "/" + totalPages + " - 等待识别");

            // 获取许可，如果没有可用许可则会阻塞
            semaphore.acquire();

            taskProgressService.addTaskProgress(file, TaskType.OCR, pageIndex + 1 + "/" + totalPages + " - 识别中...");

            try {
                // 使用 OCR 识别页面内容
                doOCR(writer, imageForOcr, imageMagickProcessor.generateOrcTempImagePath(username), null);
            } finally {
                Files.delete(Path.of(imageForOcr));
                taskProgressService.removeTaskProgress(file);
                // 释放许可
                semaphore.release();
            }
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        }  catch (Exception e) {
            log.error("Error processing page {}", pageIndex + 1, e);
        }
    }

    public void doOCR(Writer writer, String imagePath, String tempImagePath, String ocrEngine) {
        OcrConfig config = getOcrConfig();
        if (Boolean.FALSE.equals(config.getEnable())) {
            return;
        }
        if (CharSequenceUtil.isBlank(ocrEngine)) {
            ocrEngine = config.getOcrEngine();
        }
        IOcrService ocrService = ocrServiceMap.get(ocrEngine);
        if (ocrService == null) {
            throw new IllegalArgumentException("Unknown OCR engine: " + ocrEngine);
        }
        ocrService.doOCR(writer, imagePath, tempImagePath);
    }

    /**
     * 动态调整并发数量
     */
    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        int currentPermits = semaphore.availablePermits();
        if (maxConcurrentRequests > currentPermits) {
            semaphore.release(maxConcurrentRequests - currentPermits);
        } else if (maxConcurrentRequests < currentPermits) {
            // 清空所有许可并重新设置
            semaphore.drainPermits();
            semaphore.release(maxConcurrentRequests);
        }
    }

    public OcrConfig getOcrConfig() {
        return ocrConfigCache.get("ocrConfig", _ -> {
            OcrConfig config = ocrConfigDAO.findOcrConfig();
            if (config != null) {
                setMaxConcurrentRequests(config.getMaxTasks());
                return config;
            }
            return new OcrConfig();
        });
    }

    /**
     * 设置Ocr配置
     *
     * @param config OcrConfig
     */
    public synchronized long setOcrConfig(OcrConfig config) {
        if (config == null) {
            throw new CommonException(ExceptionType.PARAMETERS_VALUE);
        }
        OcrConfig ocrConfig = ocrConfigDAO.findOcrConfig();
        if (ocrConfig == null) {
            ocrConfigDAO.save(config);
        } else {
            if (!semaphore.hasQueuedThreads() && semaphore.availablePermits() == ocrConfig.getMaxTasks()) {
                semaphore.drainPermits();
                semaphore.release(config.getMaxTasks());
            }
            ocrConfig.setEnable(config.getEnable());
            ocrConfig.setMaxTasks(config.getMaxTasks());
            ocrConfig.setOcrEngine(config.getOcrEngine());
            ocrConfigDAO.save(ocrConfig);
        }
        ocrConfigCache.put("ocrConfig", config);
        return 0;
    }

}

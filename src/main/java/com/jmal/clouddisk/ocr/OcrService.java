package com.jmal.clouddisk.ocr;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jmal.clouddisk.lucene.TaskProgressService;
import com.jmal.clouddisk.lucene.TaskType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import java.util.concurrent.Semaphore;

@Service
@RequiredArgsConstructor
@Slf4j
public class OcrService {

    private final Map<String, IOcrService> ocrServiceMap;

    private final CommonOcrService commonOcrService;

    private final TaskProgressService taskProgressService;

    private final MongoTemplate mongoTemplate;

    // 初始设置为1个并发请求
    private final Semaphore semaphore = new Semaphore(1);

    private final Cache<String, OcrConfig> ocrConfigCache = Caffeine.newBuilder().build();

    /**
     * 提取PDF页面并使用OCR识别
     * @param pdfRenderer PDFRenderer
     * @param pageIndex 页码
     * @param username 用户名
     * @return 识别结果
     */
    public String extractPageWithOCR(File file, PDFRenderer pdfRenderer, int pageIndex, int totalPages, String username) {
        try {

            taskProgressService.addTaskProgress(file, TaskType.OCR, pageIndex + 1 + "/" + totalPages + " - 等待识别");

            // 获取许可，如果没有可用许可则会阻塞
            semaphore.acquire();

            taskProgressService.addTaskProgress(file, TaskType.OCR, pageIndex + 1 + "/" + totalPages + " - 识别中...");

            BufferedImage pageImage = pdfRenderer.renderImageWithDPI(pageIndex, 300);
            String tempImageFile = generateOrcTempImagePath(username);
            ImageIO.write(pageImage, "png", new File(tempImageFile));
            try {
                // 使用 OCR 识别页面内容
                return doOCR(tempImageFile, generateOrcTempImagePath(username), null);
            } finally {
                FileUtil.del(tempImageFile);
                taskProgressService.removeTaskProgress(file);
                // 释放许可
                semaphore.release();
            }
        } catch (Exception e) {
            log.error("Error processing page {}", pageIndex + 1, e);
            return "";
        }
    }

    public String doOCR(String imagePath, String tempImagePath, String ocrEngine) {
        OcrConfig config = getOcrConfig();
        if (!config.getEnable()) {
            return "";
        }
        if (StrUtil.isBlank(ocrEngine)) {
            ocrEngine = config.getOcrEngine();
        }
        IOcrService ocrService = ocrServiceMap.get(ocrEngine);
        if (ocrService == null) {
            throw new IllegalArgumentException("Unknown OCR engine: " + ocrEngine);
        }
        return ocrService.doOCR(imagePath, tempImagePath);
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
        return ocrConfigCache.get("ocrConfig", key -> {
            OcrConfig config = mongoTemplate.findOne(new Query(), OcrConfig.class);
            if (config != null) {
                setMaxConcurrentRequests(config.getMaxTasks());
                return config;
            }
            return new OcrConfig();
        });
    }

    private static Update getOcrConfigUpdate(OcrConfig config) {
        Update update = new Update();
        update.set("enable", config.getEnable());
        update.set("maxTasks", config.getMaxTasks());
        update.set("ocrEngine", config.getOcrEngine());
        return update;
    }

    /**
     * 设置Ocr配置
     *
     * @param config OcrConfig
     */
    public long setOcrConfig(OcrConfig config) {
        if (config == null) {
            return 0;
        }
        Query query = new Query();
        OcrConfig ocrConfig = mongoTemplate.findOne(query, OcrConfig.class);
        if (ocrConfig == null) {
            mongoTemplate.save(config);
        } else {
            Update update = getOcrConfigUpdate(config);
            mongoTemplate.updateFirst(query, update, OcrConfig.class);
            if (!semaphore.hasQueuedThreads() && semaphore.availablePermits() == ocrConfig.getMaxTasks()) {
                semaphore.drainPermits();
                semaphore.release(config.getMaxTasks());
            }
        }
        ocrConfigCache.put("ocrConfig", config);
        return 0;
    }

    /**
     * 生成一个临时的图片路径
     */
    public String generateOrcTempImagePath(String username) {
        return commonOcrService.generateOrcTempImagePath(username);
    }

}

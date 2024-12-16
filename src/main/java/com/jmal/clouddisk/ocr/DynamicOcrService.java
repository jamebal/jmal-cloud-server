package com.jmal.clouddisk.ocr;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.Semaphore;

@Service
@RequiredArgsConstructor
@Slf4j
public class DynamicOcrService {

    private final Map<String, IOcrService> ocrServiceMap;

    private final CommonOcrService commonOcrService;

    // 初始设置为1个并发请求
    private final Semaphore semaphore = new Semaphore(1);

    public String doOCR(String imagePath, String tempImagePath, String userChoice) {
        IOcrService ocrService = ocrServiceMap.get(userChoice);
        if (ocrService == null) {
            throw new IllegalArgumentException("No strategy found for key: " + userChoice);
        }
        try {
            // 获取许可，如果没有可用许可则会阻塞
            semaphore.acquire();
            return ocrService.doOCR(imagePath, tempImagePath);
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
        } finally {
            // 释放许可
            semaphore.release();
        }
        return "";
    }

    /**
     * 动态调整并发数量
     * @param maxConcurrentRequests 最大并发请求数
     */
    public void setMaxConcurrentRequests(int maxConcurrentRequests) {
        int currentPermits = semaphore.availablePermits();
        if (maxConcurrentRequests > currentPermits) {
            semaphore.release(maxConcurrentRequests - currentPermits);
        } else if (maxConcurrentRequests < currentPermits) {
            semaphore.drainPermits(); // 清空所有许可
            semaphore.release(maxConcurrentRequests);
        }
    }

    /**
     * 生成一个临时的图片路径
     */
    public String generateOrcTempImagePath(String username) {
        return commonOcrService.generateOrcTempImagePath(username);
    }

}

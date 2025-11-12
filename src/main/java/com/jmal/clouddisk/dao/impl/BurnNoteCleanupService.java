package com.jmal.clouddisk.dao.impl;

import com.jmal.clouddisk.dao.IBurnNoteDAO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class BurnNoteCleanupService {

    // 任务是否需要运行的标志，默认为false
    private final AtomicBoolean cleanupScheduled = new AtomicBoolean(true);

    private final IBurnNoteDAO burnNoteDAO;

    public void scheduleCleanupIfNecessary() {
        // 开启任务
        cleanupScheduled.compareAndSet(false, true);
    }

    @Scheduled(cron = "0/10 * * * * ?")
    public void cleanupExpiredNotes() {
        // 如果任务不需要运行，直接返回
        if (!cleanupScheduled.get()) {
            return;
        }
        try {
            long deletedCount = burnNoteDAO.deleteExpiredNotes();
            if (deletedCount > 0) {
                log.info("清理了 {} 个过期笔记", deletedCount);
                if (burnNoteDAO.existData()) {
                    // 如果还有过期数据，继续安排下一次清理
                    scheduleCleanupIfNecessary();
                } else {
                    // 没有更多过期数据，停止任务
                    cleanupScheduled.set(false);
                }
            }
        } catch (Exception e) {
            log.error("清理过期笔记失败", e);
        }
    }
}

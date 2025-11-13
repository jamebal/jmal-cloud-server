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

    // 任务是否需要运行的标志，默认为true
    private final AtomicBoolean cleanupScheduled = new AtomicBoolean(true);

    private final IBurnNoteDAO burnNoteDAO;

    public void scheduleCleanupIfNecessary() {
        // 开启任务
        cleanupScheduled.compareAndSet(false, true);
    }

    @Scheduled(cron = "0 * * * * ?")
    public void cleanupExpiredNotes() {
        // 如果任务不需要运行，直接返回
        if (!cleanupScheduled.get()) {
            return;
        }
        try {
            if (!burnNoteDAO.existData()) {
                // 没有更多过期数据，停止任务
                cleanupScheduled.set(false);
                log.info("已无阅后即焚笔记，清理任务暂停");
                return;
            }
            long deletedCount = burnNoteDAO.deleteExpiredNotes();
            if (deletedCount > 0) {
                log.info("清理了 {} 个过期的阅后即焚笔记", deletedCount);
            }
        } catch (Exception e) {
            log.error("清理过期的阅后即焚笔记失败", e);
        }
    }
}

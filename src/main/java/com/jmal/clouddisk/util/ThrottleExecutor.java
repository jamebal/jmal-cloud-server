package com.jmal.clouddisk.util;

import cn.hutool.core.thread.ThreadUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

@Slf4j
public class ThrottleExecutor {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, ThreadUtil.createThreadFactory("throttle-executor"));
    private ScheduledFuture<?> scheduledFuture;
    private final long delay;

    public ThrottleExecutor(long delay) {
        this.delay = delay;
    }

    public void schedule(Runnable command) {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(false);
        }
        scheduledFuture = scheduler.schedule(command, delay, TimeUnit.MILLISECONDS);
    }

    public void cancel() {
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(false);
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    public static void main(String[] args) {
        ThrottleExecutor throttleExecutor = new ThrottleExecutor(500); // 节流时间500毫秒

        for (int i = 0; i < 10; i++) {
            final int param = i;
            throttleExecutor.schedule(() -> System.out.println("Executing task with param: " + param));
            try {
                Thread.sleep(100); // 模拟频繁调用
            } catch (InterruptedException e) {
                log.error("Error occurred: ", e);
            }
        }

        throttleExecutor.shutdown();
    }
}

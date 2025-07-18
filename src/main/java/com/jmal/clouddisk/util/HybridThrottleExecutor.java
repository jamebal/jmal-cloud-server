package com.jmal.clouddisk.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 混合节流执行器
 */
public class HybridThrottleExecutor {
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private ScheduledFuture<?> scheduledFuture;
    private final long delay;
    private volatile long lastExecuteTime = 0;
    private volatile boolean isFirstCall = true;
    private volatile Runnable pendingTask = null;
    private final Object lock = new Object();

    public HybridThrottleExecutor(long delay) {
        this.delay = delay;
    }

    public void execute(Runnable command) {
        synchronized (lock) {
            long currentTime = System.currentTimeMillis();

            if (isFirstCall) {
                // 第一次调用立即执行
                isFirstCall = false;
                lastExecuteTime = currentTime;
                scheduler.submit(command);
                return;
            }

            // 保存最新的任务（确保不丢失）
            pendingTask = command;

            // 取消之前的延迟任务
            if (scheduledFuture != null && !scheduledFuture.isDone()) {
                scheduledFuture.cancel(false);
            }

            long timeSinceLastExecution = currentTime - lastExecuteTime;

            if (timeSinceLastExecution >= delay) {
                // 如果距离上次执行已经超过延迟时间，立即执行
                lastExecuteTime = currentTime;
                Runnable taskToExecute = pendingTask;
                pendingTask = null;
                scheduler.submit(taskToExecute);
            } else {
                // 计算还需要等待的时间
                long remainingDelay = delay - timeSinceLastExecution;
                scheduledFuture = scheduler.schedule(() -> {
                    synchronized (lock) {
                        if (pendingTask != null) {
                            lastExecuteTime = System.currentTimeMillis();
                            Runnable taskToExecute = pendingTask;
                            pendingTask = null;
                            taskToExecute.run();
                        }
                    }
                }, remainingDelay, TimeUnit.MILLISECONDS);
            }
        }
    }

    public void cancel() {
        synchronized (lock) {
            if (scheduledFuture != null && !scheduledFuture.isDone()) {
                scheduledFuture.cancel(false);
            }
            pendingTask = null;
        }
    }

    public void reset() {
        synchronized (lock) {
            isFirstCall = true;
            lastExecuteTime = 0;
            cancel();
        }
    }

    public void shutdown() {
        scheduler.shutdown();
    }

    public static void main(String[] args) {
        HybridThrottleExecutor executor = new HybridThrottleExecutor(500); // 节流时间500毫秒

        System.out.println("开始测试混合节流，当前时间：" + System.currentTimeMillis());

        // 测试1: 快速连续调用
        System.out.println("\n=== 测试1: 快速连续调用 ===");
        for (int i = 0; i < 5; i++) {
            final int param = i;
            System.out.println("提交任务 " + param + "，时间：" + System.currentTimeMillis());
            executor.execute(() -> System.out.println("执行任务 " + param + "，时间：" + System.currentTimeMillis()));
            try {
                Thread.sleep(100); // 快速调用，间隔小于节流时间
            } catch (InterruptedException e) {
                System.err.println("Error occurred: " + e.getMessage());
            }
        }

        // 等待一段时间
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            System.err.println("Error occurred: " + e.getMessage());
        }

        // 测试2: 慢速调用
        System.out.println("\n=== 测试2: 慢速调用（间隔大于节流时间） ===");
        for (int i = 10; i < 13; i++) {
            final int param = i;
            System.out.println("提交任务 " + param + "，时间：" + System.currentTimeMillis());
            executor.execute(() -> System.out.println("执行任务 " + param + "，时间：" + System.currentTimeMillis()));
            try {
                Thread.sleep(600); // 慢速调用，间隔大于节流时间
            } catch (InterruptedException e) {
                System.err.println("Error occurred: " + e.getMessage());
            }
        }

        // 等待所有任务完成
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            System.err.println("Error occurred: " + e.getMessage());
        }

        executor.shutdown();
    }
}

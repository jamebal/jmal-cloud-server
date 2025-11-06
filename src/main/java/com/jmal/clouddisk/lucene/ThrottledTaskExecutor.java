package com.jmal.clouddisk.lucene;

import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class ThrottledTaskExecutor {

    private final ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
    private final AtomicInteger pendingTaskCount = new AtomicInteger(0);
    private final Semaphore permits;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition isIdleCondition = lock.newCondition();

    public ThrottledTaskExecutor(int maxConcurrentTasks) {
        if (maxConcurrentTasks <= 0) {
            throw new IllegalArgumentException("最大并发任务数必须为正数。");
        }
        this.permits = new Semaphore(maxConcurrentTasks);
    }

    public void execute(Runnable task) {
        pendingTaskCount.incrementAndGet();
        delegate.execute(() -> {
            try {
                permits.acquire();
                try {
                    task.run();
                } catch (Exception e) {
                    log.error("任务执行失败，出现异常。", e);
                } finally {
                    permits.release();
                }
            } catch (InterruptedException e) {
                log.warn("任务在等待许可时被中断。");
                Thread.currentThread().interrupt();
            } finally {
                if (pendingTaskCount.decrementAndGet() == 0) {
                    signalAllWaiters();
                }
            }
        });
    }

    private void signalAllWaiters() {
        lock.lock();
        try {
            isIdleCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        long nanos = unit.toNanos(timeout);
        lock.lock();
        try {
            while (!isIdle()) {
                if (nanos <= 0) {
                    return false;
                }
                nanos = isIdleCondition.awaitNanos(nanos);
            }
            return true;
        } finally {
            lock.unlock();
        }
    }

    public boolean isIdle() {
        return pendingTaskCount.get() == 0;
    }

    public void shutdown() {
        delegate.shutdown();
        try {
            if (awaitTermination(15, TimeUnit.SECONDS)) {
                log.debug("执行器优雅地终止。");
            } else {
                log.warn("执行器在15秒内未优雅地终止。强制关闭中...");
                // 尝试强制关闭
                delegate.shutdownNow();
                // 再次等待一小段时间以确保强制关闭生效
                if (!delegate.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.error("执行器即使在强制关闭后也没有终止。");
                }
            }
        } catch (InterruptedException e) {
            log.error("执行器关闭被中断。现在强制关闭。");
            delegate.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

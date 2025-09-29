package com.jmal.clouddisk.dao.impl.jpa.write;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 带有优先级的队列化写入服务的实现。
 * 当数据源是SQLite时激活。
 * 它将写入任务放入一个内存优先级队列，由一个后台单线程消费者串行执行。
 * 高优先级的任务（如用户操作）会比普通优先级的任务（如后台任务）先被执行。
 */
@Slf4j
public class QueuedWriteServiceImpl implements IWriteService {

    // 内部任务封装类，现在实现了 Comparable 接口
    private static class WriteTask<R> implements Comparable<WriteTask<?>> {

        // 用于在优先级相同时，保证先进先出
        private static final AtomicLong sequencer = new AtomicLong();

        private final IDataOperation<R> operation;
        private final CompletableFuture<R> future;
        private final Priority priority;
        private final long sequenceNumber; // 序列号

        private WriteTask(IDataOperation<R> operation, CompletableFuture<R> future, Priority priority) {
            this.operation = operation;
            this.future = future;
            this.priority = priority;
            this.sequenceNumber = sequencer.getAndIncrement();
        }

        @Override
        public int compareTo(WriteTask<?> other) {
            // 首先比较优先级。枚举的 ordinal() 返回其定义顺序（从0开始），值越小优先级越高。
            int priorityDiff = this.priority.ordinal() - other.priority.ordinal();
            if (priorityDiff != 0) {
                return priorityDiff;
            }
            // 如果优先级相同，则比较序列号，保证FIFO（先进先出）
            return Long.compare(this.sequenceNumber, other.sequenceNumber);
        }
    }

    // 毒丸是一个特殊的WriteTask，拥有最低优先级
    private static final class PoisonPill extends WriteTask<Void> {
        private PoisonPill() {
            super(null, null, Priority.LOWEST);
        }
    }

    private static final WriteTask<?> POISON_PILL = new PoisonPill();

    private final BlockingQueue<WriteTask<?>> writeQueue = new PriorityBlockingQueue<>(10000);

    private static class MonitoredThreadFactory implements ThreadFactory {
        private final ThreadFactory delegate = Executors.defaultThreadFactory();

        @Override
        public Thread newThread(@NotNull Runnable r) {
            Thread thread = delegate.newThread(r);
            // 为每个新线程安装一个异常处理器
            thread.setUncaughtExceptionHandler((t, e) -> {
                // 这是关键！任何未 被捕获的异常（包括导致线程死亡的Error）都会在这里被记录
                log.error("!!! UNCAUGHT EXCEPTION IN WRITER THREAD !!! Thread: {}", t.getName(), e);
            });
            return thread;
        }
    }

    private final ExecutorService writerExecutor = Executors.newSingleThreadExecutor(new MonitoredThreadFactory());

    private final DataManipulationService dataManipulationService;

    public QueuedWriteServiceImpl(DataManipulationService dataManipulationService) {
        this.dataManipulationService = dataManipulationService;
        log.info("写入策略初始化：带优先级的异步队列写入（适用于SQLite）。");
        init();
    }

    @Override
    public <R> CompletableFuture<R> submit(IDataOperation<R> operation, Priority priority) {
        log.info( "提交操作任务 {}, 优先级: {}", operation.getClass().getName(), priority);
        WriteTask<R> task = new WriteTask<>(operation, new CompletableFuture<>(), priority);

        boolean offered = writeQueue.offer(task);

        if (!offered) {
            String errorMessage = "写入队列已满, " + priority + " 优先级的操作任务 " + operation.getClass().getName() + " 被拒绝。";
            log.error(errorMessage);
            task.future.completeExceptionally(new RejectedExecutionException(errorMessage));
        } else {
            log.info("任务 {} 已提交，优先级: {}", operation.getClass().getSimpleName(), priority);
        }

        return task.future;
    }

    public void init() {
        log.info("启动队列写入服务消费者线程...");
        writerExecutor.submit(() -> {
            try {
                log.info("队列写入服务消费者线程已启动。");
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        WriteTask<?> task = writeQueue.take();

                        if (task == POISON_PILL) {
                            log.debug("毒丸接收。消费者线程正在优雅地停止。");
                            drainQueueOnShutdown();
                            break;
                        }

                        processTask(task);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Throwable t) {
                log.error("队列写入服务消费者线程遇到致命错误并终止。", t);
            } finally {
                log.info("队列写入服务消费者线程已终止。");
            }
        });
    }

    private void processTask(WriteTask<?> task) {
        try {
            log.info("处理操作任务 {}, 优先级: {}", task.operation.getClass().getName(), task.priority);
            Object result = dataManipulationService.execute(task.operation);
            completeFuture(task.future, result);
        } catch (Throwable e) {
            log.error("处理操作写入任务时出错 {}", task.operation.getClass().getName(), e);
            if (task.future != null) {
                task.future.completeExceptionally(e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <R> void completeFuture(CompletableFuture<R> future, Object result) {
        future.complete((R) result);
    }

    private void drainQueueOnShutdown() {
        WriteTask<?> remainingTask;
        int drainedCount = 0;
        while ((remainingTask = writeQueue.poll()) != null) {
            if (remainingTask != POISON_PILL) {
                log.warn("在关闭时排空任务 {}", remainingTask.operation.getClass().getName());
                remainingTask.future.completeExceptionally(
                        new CancellationException("服务正在关闭，任务未处理。")
                );
                drainedCount++;
            }
        }
        if (drainedCount > 0) {
            log.warn("在关闭过程中，从队列中清除了{}个已排空和已取消的任务。", drainedCount);
        }
    }

    @PreDestroy
    private void stopConsumer() {
        log.debug("启动QueuedWriteService的优雅关闭...");
        writerExecutor.shutdown();
        try {
            writeQueue.put(POISON_PILL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("尝试发送毒丸时被中断。");
        }
        try {
            if (!writerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("消费者线程未及时终止。强制关闭...");
                writerExecutor.shutdownNow();
            } else {
                log.debug("队列写入服务优雅关闭。");
            }
        } catch (InterruptedException e) {
            writerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

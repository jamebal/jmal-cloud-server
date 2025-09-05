package com.jmal.clouddisk.dao.impl.jpa.write;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.*;

/**
 * 队列化写入服务的实现。
 * 当数据源是SQLite时激活。
 * 它将写入任务放入一个内存队列，由一个后台单线程消费者串行执行。
 */
@Slf4j
public class QueuedWriteServiceImpl implements IWriteService {

    // 内部任务封装类
    private static class WriteTask {
        private final IDataOperation operation;
        private final CompletableFuture<Void> future;

        private WriteTask(IDataOperation operation, CompletableFuture<Void> future) {
            this.operation = operation;
            this.future = future;
        }

        /**
         * 工厂方法，用于创建常规任务
         */
        static WriteTask forOperation(IDataOperation operation) {
            return new WriteTask(operation, new CompletableFuture<>());
        }
    }

    private static final class PoisonPill extends WriteTask {
        // 毒丸是一个特殊的WriteTask
        private PoisonPill() {
            // payload和future都可以是null，因为它只是一个信号
            super(null, null);
        }
    }

    private static final WriteTask POISON_PILL = new PoisonPill();

    private final BlockingQueue<WriteTask> writeQueue = new LinkedBlockingQueue<>(10000);
    private final ExecutorService writerExecutor = Executors.newSingleThreadExecutor();
    private final DataManipulationService dataManipulationService;

    public QueuedWriteServiceImpl(DataManipulationService dataManipulationService) {
        this.dataManipulationService = dataManipulationService;
        log.debug("写入策略初始化：异步队列写入（适用于SQLite）。");
    }

    @Override
    public CompletableFuture<Void> submit(IDataOperation operation) {
        WriteTask task = WriteTask.forOperation(operation);

        boolean offered = writeQueue.offer(task);

        if (!offered) {
            String errorMessage = "写入队列已满, 操作任务 " + operation.getClass().getName() + " 被拒绝。";
            log.error(errorMessage);
            // 如果队列已满，立即让Future失败
            task.future.completeExceptionally(new RejectedExecutionException(errorMessage));
        }

        return task.future;
    }

    @PostConstruct
    private void startConsumer() {
        writerExecutor.submit(() -> {
            log.debug("队列写入服务消费者线程已启动。");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 使用 take() 高效阻塞，直到有任务或毒丸到来
                    WriteTask task = writeQueue.take();

                    // 检查是否是关闭信号
                    if (task == POISON_PILL) {
                        log.debug("毒丸接收。消费者线程正在优雅地停止。");
                        // 清理队列中剩余的任务
                        drainQueueOnShutdown();
                        // 退出循环，结束线程
                        break;
                    }

                    // 处理常规任务
                    processTask(task);

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
    }

    private void processTask(WriteTask task) {
        try {
            log.debug("处理操作任务 {}", task.operation.getClass().getName());
            dataManipulationService.execute(task.operation);
            task.future.complete(null);
        } catch (Exception e) {
            log.error("处理操作写入任务时出错 {}", task.operation.getClass().getName(), e);
            if (task.future != null) {
                task.future.completeExceptionally(e);
            }
        }
    }

    private void drainQueueOnShutdown() {
        WriteTask remainingTask;
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

        // 1. 命令执行器不再接受新任务
        writerExecutor.shutdown();

        // 2. 向队列中放入“毒丸”，以唤醒并终止消费者线程
        try {
            // 使用put确保毒丸一定能被放入队列，即使队列满了（虽然不太可能）
            writeQueue.put(POISON_PILL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("尝试发送毒丸时被中断。");
        }

        // 3. 等待线程池彻底终止
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

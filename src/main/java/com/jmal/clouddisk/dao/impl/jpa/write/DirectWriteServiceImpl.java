package com.jmal.clouddisk.dao.impl.jpa.write;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * 直接写入服务的实现。
 * 当数据源不是SQLite时激活。
 * 它会同步地、立即地执行数据操作。
 */
@Slf4j
public class DirectWriteServiceImpl implements IWriteService {

    private final DataManipulationService dataManipulationService;

    public DirectWriteServiceImpl(DataManipulationService dataManipulationService) {
        this.dataManipulationService = dataManipulationService;
        log.debug("写入策略初始化：直接同步写入（适用于MySQL/PostgreSQL等）。");
    }

    @Override
    public <R> CompletableFuture<R> submit(IDataOperation<R> operation, Priority priority) {
        try {
            // 直接、同步地调用通用的数据操作执行器
            R result = dataManipulationService.execute(operation);

            // 操作成功，返回一个已经完成的Future
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("Direct write failed for operation {}", operation.getClass().getName(), e);

            // 操作失败，返回一个已完成但带有异常的Future
            return CompletableFuture.failedFuture(e);
        }
    }
}

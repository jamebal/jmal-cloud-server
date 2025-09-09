package com.jmal.clouddisk.dao.impl.jpa.write;

import java.util.concurrent.CompletableFuture;

/**
 * 通用写入服务接口。
 * 定义了提交写入任务的统一合约。
 * 上层服务（如业务Service、Controller）应该只依赖此接口
 * 而不关心底层的实现是同步的还是异步的。
 */
public interface IWriteService {

    /**
     * 提交一个高优先级数据操作任务。
     * @param operation 要执行的数据操作。
     * @param <R> 操作期望返回的结果类型。
     * @return 一个 CompletableFuture，最终将保存操作的结果。
     */
    default <R> CompletableFuture<R> submit(IDataOperation<R> operation) {
        return submit(operation, Priority.HIGH);
    }

    /**
     * 提交一个带有指定优先级的写操作任务。
     *
     * @param operation 要执行的数据操作
     * @param priority  任务的优先级
     * @param <R>       操作的返回类型
     * @return 一个代表异步操作结果的 CompletableFuture
     */
    <R> CompletableFuture<R> submit(IDataOperation<R> operation, Priority priority);

}

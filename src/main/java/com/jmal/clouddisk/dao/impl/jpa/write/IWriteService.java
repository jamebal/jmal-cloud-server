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
     * 提交一个数据操作任务，并异步返回其结果。
     * @param operation 要执行的数据操作。
     * @param <R> 操作期望返回的结果类型。
     * @return 一个 CompletableFuture，最终将保存操作的结果。
     */
    <R> CompletableFuture<R> submit(IDataOperation<R> operation);

}

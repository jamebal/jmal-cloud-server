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
     * 提交一个数据操作任务。
     *
     * @param operation 要执行的数据操作，必须是 IDataOperation 的一个实例。
     * @return a CompletableFuture that completes when the write operation is confirmed,
     *         or completes exceptionally if it fails. For queued implementations,
     *         this completion happens when the task is processed by the consumer.
     *         For direct implementations, this completion happens immediately.
     */
    CompletableFuture<Void> submit(IDataOperation operation);

}

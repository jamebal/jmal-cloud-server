package com.jmal.clouddisk.dao.impl.jpa.write;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

@RequiredArgsConstructor
@Slf4j
public class SafeWriteServiceDecorator implements IWriteService {

    private final IWriteService delegate;

    @Override
    public <R> CompletableFuture<R> submit(IDataOperation<R> operation) {
        return delegate.submit(operation)
                .exceptionally(ex -> {
                    log.error("在未处理的后台写入任务中发生了一个异常，涉及某个操作 : {}",
                            operation.getClass().getName(), ex);
                    // 为了让类型匹配，我们需要让异常“传播”
                    throw new CompletionException(ex);
                });
    }
}

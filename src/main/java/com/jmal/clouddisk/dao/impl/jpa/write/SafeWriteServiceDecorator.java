package com.jmal.clouddisk.dao.impl.jpa.write;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
@Slf4j
public class SafeWriteServiceDecorator implements IWriteService {

    private final IWriteService delegate;

    @Override
    public CompletableFuture<Void> submit(IDataOperation operation) {
        CompletableFuture<Void> originalFuture = delegate.submit(operation);

        return originalFuture.exceptionally(ex -> {
            log.error("在未处理的后台写入任务中发生了一个异常，涉及某个操作 : {}",
                      operation.getClass().getName(), ex);
            // 必须返回null，这是exceptionally方法的要求
            return null;
        });
    }
}

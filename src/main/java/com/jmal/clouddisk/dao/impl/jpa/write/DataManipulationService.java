package com.jmal.clouddisk.dao.impl.jpa.write;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DataManipulationService {

    private final Map<Class<? extends IDataOperation>, IDataOperationHandler<?>> handlers;

    public DataManipulationService(List<IDataOperationHandler<?>> handlerList) {
        // 遍历所有Handler
        this.handlers = handlerList.stream()
                // 对每个Handler，获取它支持的所有操作类型，并创建 (操作类型 -> Handler) 的映射条目
                .flatMap(handler -> handler.supportedOperationTypes().stream()
                        .map(opType -> Map.entry(opType, handler))
                )
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Transactional
    public void execute(IDataOperation operation) {
        if (operation == null) {
            throw new IllegalArgumentException("Operation cannot be null.");
        }

        final IDataOperationHandler<?> handler = handlers.get(operation.getClass());

        if (handler == null) {
            throw new UnsupportedOperationException("No handler found for operation: " + operation.getClass().getName());
        }

        invokeHandler(handler, operation);
    }


    /**
     * 一个私有的、类型安全的辅助方法，用于调用handler。
     * @param handler 待执行的处理器
     * @param operation 要处理的操作
     * @param <T> 操作的具体类型
     */
    @SuppressWarnings("unchecked")
    private <T extends IDataOperation> void invokeHandler(IDataOperationHandler<T> handler, IDataOperation operation) {
        handler.handle((T) operation);
    }
}

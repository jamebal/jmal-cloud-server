package com.jmal.clouddisk.dao.impl.jpa.write;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class DataManipulationService {

    private final Map<Class<? extends IDataOperation<?>>, IDataOperationHandler<?, ?>> handlers;

    public DataManipulationService(Map<Class<? extends IDataOperation<?>>, IDataOperationHandler<?, ?>> handlers) {
        this.handlers = handlers;
    }

    @Transactional
    @SuppressWarnings("unchecked")
    public <R> R execute(IDataOperation<R> operation) {
        if (operation == null) {
            throw new IllegalArgumentException("Operation cannot be null.");
        }

        IDataOperationHandler<IDataOperation<R>, R> handler =
                (IDataOperationHandler<IDataOperation<R>, R>) handlers.get(operation.getClass());

        if (handler == null) {
            throw new UnsupportedOperationException("No handler found for operation: " + operation.getClass().getName());
        }

        return handler.handle(operation);
    }

}

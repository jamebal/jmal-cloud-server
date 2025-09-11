package com.jmal.clouddisk.dao.impl.jpa.write.log;

import com.jmal.clouddisk.model.LogOperation;

public final class LogDataOperation {
    private LogDataOperation() {}

    public record CreateAll(Iterable<com.jmal.clouddisk.model.LogOperation> entities) implements ILogDataOperation<Void> {}
    public record Create(LogOperation entity) implements ILogDataOperation<LogOperation> {}
}

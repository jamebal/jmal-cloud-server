package com.jmal.clouddisk.dao.impl.jpa;

public interface IWriteCommon<T> {
    void AsyncSaveAll(Iterable<T> entities);
}

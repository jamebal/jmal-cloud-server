package com.jmal.clouddisk.dao.impl.jpa;

public interface IWriteCommon<T> {
   default void AsyncSaveAll(Iterable<T> entities) {}
}

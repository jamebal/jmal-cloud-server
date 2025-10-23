package com.jmal.clouddisk.dao.impl.jpa.write;

/**
 * 一个处理特定类型数据操作的处理器。
 * @param <T> 它能处理的操作类型，继承自IDataOperation
 * @param <R> 操作返回的结果类型。
 */
public interface IDataOperationHandler<T extends IDataOperation<R>, R> {

    /**
     * 处理给定的操作。
     * @param operation 要处理的操作对象。
     */
    R handle(T operation);
}

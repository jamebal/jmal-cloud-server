package com.jmal.clouddisk.dao.impl.jpa.write;

import org.springframework.core.GenericTypeResolver;

import java.lang.reflect.Type;

/**
 * 一个自执行的数据操作命令。
 * 命令对象封装了执行所需的所有信息。
 * @param <R> 此操作执行后期望返回的结果类型。
 */
public interface IDataOperation<R> {

    @SuppressWarnings("unchecked")
    default Class<R> getReturnType() {
        Type returnType = GenericTypeResolver.resolveTypeArgument(this.getClass(), IDataOperation.class);
        return (Class<R>) returnType;
    }

}

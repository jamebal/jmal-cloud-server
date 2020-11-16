package com.jmal.clouddisk.exception;


@FunctionalInterface
public interface CheckedFunction<T,R> {
    R apply(T t) throws Exception;
}

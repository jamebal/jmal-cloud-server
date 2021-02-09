package com.jmal.clouddisk.annotation;



import com.jmal.clouddisk.model.LogOperation;

import java.lang.annotation.*;

/**
 * 操作方法日志注解
 * @author jmal
 */
@Documented
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogOperatingFun {
    /**
     * 操作功能
     */
    String value() default "";
    /***
     * 日志类型
     */
    LogOperation.Type logType() default LogOperation.Type.OPERATION;
}

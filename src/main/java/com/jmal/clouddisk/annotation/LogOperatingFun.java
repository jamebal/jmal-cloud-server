package com.jmal.clouddisk.annotation;



import java.lang.annotation.*;

/**
 * 操作方法日志注解
 * @author jmal
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
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

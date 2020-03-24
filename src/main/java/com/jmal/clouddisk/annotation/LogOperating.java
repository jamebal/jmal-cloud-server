package com.jmal.clouddisk.annotation;



import java.lang.annotation.*;

/**
 * 操作日志注解
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface LogOperating {
    /**
     * 日志信息
     * @return
     */
    String logInfo() default "";

    OperationType operat() default OperationType.read;
}

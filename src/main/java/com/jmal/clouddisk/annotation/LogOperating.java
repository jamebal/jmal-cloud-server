package com.jmal.clouddisk.annotation;



import java.lang.annotation.*;

/**
 * 操作日志注解
 * @author jmal
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LogOperating {
    /**
     * 操作模块
     */
    String module() default "";
}

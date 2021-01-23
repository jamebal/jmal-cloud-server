package com.jmal.clouddisk.annotation;



import java.lang.annotation.*;

/**
 * 操作日志注解
 * @author jmal
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Permission {

    /***
     * @return 权限标识
     */
    String value() default "";

    /***
     * @return 只有晚盘创建者才能通过
     */
    boolean onlyCreator() default false;

}

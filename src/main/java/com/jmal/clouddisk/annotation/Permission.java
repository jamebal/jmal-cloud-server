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
     * 权限标识
     * @return
     */
    String value() default "";

    /***
     * 角色标识，只有该角色才能通过
     * @return
     */
    String only() default "Administrators";

}

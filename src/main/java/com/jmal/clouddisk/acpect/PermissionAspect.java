package com.jmal.clouddisk.acpect;

import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResultUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;


import java.lang.reflect.Method;
import java.util.List;

/**
 * @Description 授权切面
 * @blame jmal
 * @Date 2021/1/8 8:38 下午
 */
@Aspect
@Component
public class PermissionAspect {

    @Autowired
    private UserLoginHolder userLoginHolder;

    @Autowired
    private UserServiceImpl userService;

    @Pointcut("@annotation(com.jmal.clouddisk.annotation.Permission)")
    public void privilege() {
    }

    @Around("privilege()")
    public Object before(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method targetMethod = methodSignature.getMethod();
        final Permission permission = privilegeParse(targetMethod);
        if (permission == null) {
            // 方法上没有权限注解, 直接调用方法
            return joinPoint.proceed();
        }
        boolean onlyCreator = permission.onlyCreator();
        if (onlyCreator) {
            // 只有创建者才有权限
            if (userService.getIsCreator(userLoginHolder.getUserId())) {
                return joinPoint.proceed();
            } else {
                return ResultUtil.error(ExceptionType.PERMISSION_DENIED);
            }
        }
        String authority = permission.value();
        if (StrUtil.isBlank(authority)) {
            // 方法上没有权限注解, 直接调用方法
            return joinPoint.proceed();
        }
        // 上传前检查空间是否够用
        if ("cloud:file:upload".equals(authority) && CaffeineUtil.spaceFull(userLoginHolder.getUserId())) {
            return ResultUtil.error(ExceptionType.SPACE_FULL);
        }
        // 获取当前身份的权限
        List<String> authorities = userLoginHolder.getAuthorities();
        if (authorities != null && authorities.contains(authority)) {
            return joinPoint.proceed();
        }
        return ResultUtil.error(ExceptionType.PERMISSION_DENIED);
    }

    /***
     * 解析权限注解
     * @param method Method
     * @return Permission
     */
    public static Permission privilegeParse(Method method) {
        if (method.isAnnotationPresent(Permission.class)) {
            return method.getAnnotation(Permission.class);
        }
        return null;
    }
}

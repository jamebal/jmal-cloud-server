package com.jmal.clouddisk.acpect;

import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.util.ResultUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

    @Pointcut("@annotation(com.jmal.clouddisk.annotation.Permission)" )

    public void privilege(){}

    @Around("privilege()")
    public Object before(ProceedingJoinPoint joinPoint) throws Throwable,CommonException {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method targetMethod = methodSignature.getMethod();
        final String authority = privilegeParse(targetMethod);
        if(StringUtils.isEmpty(authority)){
            // 方法上没有权限注解, 直接调用方法
            return joinPoint.proceed();
        } else {
            // 获取当前身份的权限
            ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            Object object = requestAttributes.getAttribute("authorities", 0);
            if(object != null){
                List<String> authorities = (List<String>) object;
                if(authorities.contains(authority)){
                    return joinPoint.proceed();
                }
            }
            return ResultUtil.error(ExceptionType.PERMISSION_DENIED);
        }
    }

    /***
     * 解析权限注解
     * @param method Method
     * @return 注解的authority值
     */
    public static String privilegeParse(Method method) {
        if(method.isAnnotationPresent(Permission.class)){
            Permission annotation = method.getAnnotation(Permission.class);
            return annotation.value();
        }
        return null;
    }
}

package com.jmal.clouddisk.acpect;

import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.impl.LogService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;

/***
 * @Description 操作日志切面现实
 * @author jmal
 **/
@Aspect
@Component
public class LogOperatingAspect {

    @Autowired
    private UserLoginHolder userLoginHolder;

    @Autowired
    LogService logService;

    /**
     * 切入点
     **/
    @Pointcut("@annotation(com.jmal.clouddisk.annotation.LogOperatingFun)")
    public void addAdvice() {
    }

    /**
     * 日志切面拦截器，用来记录每个请求端日志
     * 根据LogOperatingFun注解判判断是否记录日志
     * 如果LogOperatingFun注解没有value值，者使用swagger的ApiOperation注解的值
     */
    @Around("addAdvice()")
    public Object interceptor(ProceedingJoinPoint joinPoint) throws Throwable {
        String username = userLoginHolder.getUsername();
        long time = System.currentTimeMillis();
        // 执行方法
        Object result = joinPoint.proceed();
        // 执行耗时
        long timeConsuming = System.currentTimeMillis() - time;
        // 接收到请求，记录请求内容
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            LogOperation logOperation = new LogOperation();
            logOperation.setTime(timeConsuming);
            logOperation.setUsername(username);
            parserLog(joinPoint, result, attributes, logOperation);
        }
        return result;
    }

    /***
     * 解析日志
     */
    private void parserLog(ProceedingJoinPoint joinPoint, Object result, ServletRequestAttributes attributes, LogOperation logOperation) {
        // 获取类/方法上的操作日志注解
        MethodSignature methodSignature = ((MethodSignature) joinPoint.getSignature());
        //获取method对象
        Method method = methodSignature.getMethod();
        LogOperatingFun logOperatingFun = method.getAnnotation(LogOperatingFun.class);
        // swagger的ApiOperation注解, 用来获取操作说明
        Operation apiOperation = method.getAnnotation(Operation.class);
        // swagger的Api注解, 用来获取操作模块
        @SuppressWarnings("unchecked")
        Tag api = (Tag) joinPoint.getSourceLocation().getWithinType().getAnnotation(Tag.class);
        if(api != null){
            logOperation.setOperationModule(api.name());
        }
        // 操作功能
        String operationFun = logOperatingFun.value();
        if(StrUtil.isBlank(operationFun)){
            operationFun = apiOperation.summary();
        }
        logOperation.setOperationFun(operationFun);
        String logType = logOperatingFun.logType().name();
        logOperation.setType(logType);
        if(LogOperation.Type.LOGIN.name().equals(logType)){
            // 登录日志
            ConsumerDO consumerDO = (ConsumerDO) joinPoint.getArgs()[0];
            logOperation.setUsername(consumerDO.getUsername());
        }
        // 添加日志
        logService.addLogBefore(logOperation, result, attributes.getRequest(), attributes.getResponse());
    }
}

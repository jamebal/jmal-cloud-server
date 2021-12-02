package com.jmal.clouddisk.acpect;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.impl.LogService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import com.jmal.clouddisk.util.ResponseResult;
import io.milton.http.Response;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

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
        ApiOperation apiOperation = method.getAnnotation(ApiOperation.class);
        // swagger的Api注解, 用来获取操作模块
        @SuppressWarnings("unchecked")
        Api api = (Api) joinPoint.getSourceLocation().getWithinType().getAnnotation(Api.class);
        if(api != null){
            logOperation.setOperationModule(api.tags()[0]);
        }
        // 操作功能
        String operationFun = logOperatingFun.value();
        if(StrUtil.isBlank(operationFun)){
            operationFun = apiOperation.value();
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

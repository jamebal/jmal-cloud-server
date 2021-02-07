package com.jmal.clouddisk.acpect;

import cn.hutool.core.lang.Console;
import cn.hutool.core.thread.ThreadUtil;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.LogOperation;
import com.jmal.clouddisk.interceptor.AuthInterceptor;
import com.jmal.clouddisk.model.rbac.UserLoginContext;
import com.jmal.clouddisk.service.impl.LogService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.catalina.connector.ResponseFacade;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.*;

/***
 * @Description 操作日志切面现实
 * @author jmal
 **/
@Aspect
@Component
public class LogOperatingAspect {

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private UserLoginHolder userLoginHolder;

    @Autowired
    private AuthInterceptor authInterceptor;

    @Autowired
    LogService logService;

    private final ThreadFactory addLogThreadFactory = new ThreadFactoryBuilder()
            .setNameFormat("addLog").build();

    ExecutorService singleThreadPool = new ThreadPoolExecutor(1, 2,
            3L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(8), addLogThreadFactory, new ThreadPoolExecutor.AbortPolicy());

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
        Object result;
        String username = userLoginHolder.getUsername();
        long stime = System.currentTimeMillis();
        // 执行方法
        result = joinPoint.proceed();
        // 执行耗时
        long time = System.currentTimeMillis() - stime;
        // 接收到请求，记录请求内容
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            HttpServletResponse response = attributes.getResponse();
            // 获取类/方法上的操作日志注解
            MethodSignature methodSignature = ((MethodSignature) joinPoint.getSignature());
            //获取method对象
            Method method = methodSignature.getMethod();
            Console.log(method.getReturnType());
            LogOperatingFun logOperatingFun = method.getAnnotation(LogOperatingFun.class);
            // swagger的ApiOperation注解, 用来获取操作说明
            ApiOperation apiOperation = method.getAnnotation(ApiOperation.class);
            // swagger的Api注解, 用来获取操作模块
            Api api = (Api) joinPoint.getSourceLocation().getWithinType().getAnnotation(Api.class);
            LogOperation logOperation = new LogOperation();
            logOperation.setTime(time);
            if(api != null){
                logOperation.setOperationModule(api.tags()[0]);
            }
            // 操作功能
            String operationFun = logOperatingFun.value();
            if(StringUtils.isEmpty(operationFun)){
                operationFun = apiOperation.value();
            }
            logOperation.setUsername(username);
            logOperation.setOperationFun(operationFun);
            logOperation.setType(logOperatingFun.logType().name());
            //添加日志
            singleThreadPool.execute(()-> addLog(logOperation, request, (ResponseResult)result));
        }
        return result;
    }

    private void addLog(LogOperation logOperation, HttpServletRequest request, ResponseResult result) {
        // TODO 解析日志
        // 用户
        String username = logOperation.getUsername();
        if(!StringUtils.isEmpty(username)){
            logOperation.setShowName(userService.getShowNameByUserUsernme(username));
        }
        // 请求地址
        logOperation.setUrl(request.getRequestURI());
        logOperation.setMethod(request.getMethod());
        logOperation.setIp(request.getRemoteAddr());
        logService.addLog(logOperation);
        logOperation.setStatus(0);
        if(result.getCode() != 0){
            logOperation.setRemarks(result.getMessage().toString());
        }
    }

}

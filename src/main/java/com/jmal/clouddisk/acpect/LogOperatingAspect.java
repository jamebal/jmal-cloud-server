package com.jmal.clouddisk.acpect;

import cn.hutool.core.thread.ThreadUtil;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.jmal.clouddisk.annotation.LogOperating;
import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.LogOperation;
import com.jmal.clouddisk.service.impl.LogService;
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
import java.util.concurrent.*;

/***
 * @Description 操作日志切面现实
 * @author jmal
 **/
@Aspect
@Component
public class LogOperatingAspect {

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

    /***
     * 在执行方法前后调用Advice，相当于@Before和@AfterReturning一起做的事儿；
     * @param pjp
     * @return
     * @throws Throwable
     */
    @Around("addAdvice()")
    public Object interceptor(ProceedingJoinPoint pjp) throws Throwable {
        Object result;

        long stime = System.currentTimeMillis();
        // 执行方法
        result = pjp.proceed();
        // 执行耗时
        long time = System.currentTimeMillis() - stime;
        // 接收到请求，记录请求内容
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            HttpServletResponse response = attributes.getResponse();
            // 获取类/方法上的操作日志注解
            MethodSignature methodSignature = ((MethodSignature) pjp.getSignature());
            LogOperating logOperating = methodSignature.getClass().getAnnotation(LogOperating.class);
            LogOperatingFun logOperatingFun = methodSignature.getMethod().getAnnotation(LogOperatingFun.class);
            LogOperation logOperation = new LogOperation();
            logOperation.setTime(time);
            if(logOperating != null){
                logOperation.setOperationModule(logOperating.module());
            }
            logOperation.setOperationFun(logOperatingFun.value());
            logOperation.setType(logOperatingFun.logType().name());
            //添加日志
            singleThreadPool.execute(()-> addLog(logOperation, request, response));
        }
        return result;
    }

    private void addLog(LogOperation logOperation, HttpServletRequest request, HttpServletResponse response) {
        // TODO 解析日志

        logService.addLog(logOperation);
    }

}

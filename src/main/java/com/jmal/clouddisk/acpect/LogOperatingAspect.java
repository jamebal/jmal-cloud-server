package com.jmal.clouddisk.acpect;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.impl.LogService;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.poi.ss.formula.functions.T;
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
    private UserServiceImpl userService;

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
        Api api = (Api) joinPoint.getSourceLocation().getWithinType().getAnnotation(Api.class);
        if(api != null){
            logOperation.setOperationModule(api.tags()[0]);
        }
        // 操作功能
        String operationFun = logOperatingFun.value();
        if(StringUtils.isEmpty(operationFun)){
            operationFun = apiOperation.value();
        }
        logOperation.setOperationFun(operationFun);
        String logType = logOperatingFun.logType().name();
        logOperation.setType(logType);
        if(LogOperation.Type.LOGIN.name().equals(logType)){
            // 登陆日志
            ConsumerDO consumerDO = (ConsumerDO) joinPoint.getArgs()[0];
            logOperation.setUsername(consumerDO.getUsername());
        }
        // 添加日志
        addLog(logOperation, attributes, result);
    }

    private void addLog(LogOperation logOperation, ServletRequestAttributes attributes, Object result) {
        HttpServletRequest request = attributes.getRequest();
        HttpServletResponse response = attributes.getResponse();
        // 用户
        String username = logOperation.getUsername();
        if (!StringUtils.isEmpty(username)) {
            logOperation.setShowName(userService.getShowNameByUserUsernme(username));
        }
        // UserAgent
        UserAgent userAgent = UserAgentUtil.parse(request.getHeader("User-Agent"));
        if (userAgent != null){
            logOperation.setOperatingSystem(userAgent.getOs().getName());
            logOperation.setDeviceModel(userAgent.getPlatform().getName());
            logOperation.setBrowser(userAgent.getBrowser().getName() + userAgent.getVersion());
        }
        // 请求地址
        logOperation.setUrl(request.getRequestURI());
        // 请求方式
        logOperation.setMethod(request.getMethod());
        // 客户端地址
        logOperation.setIp(request.getRemoteAddr());
        // 请求参数
        Map<String, String> params = new HashMap<>(16);
        Enumeration<String> enumeration = request.getParameterNames();
        while (enumeration.hasMoreElements()){
            String key = enumeration.nextElement();
            params.put(key, request.getParameter(key));
        }
        logOperation.setParams(params);
        // 返回结果
        logOperation.setStatus(0);
        ResponseResult<Object> responseResult;
        try {
            responseResult = (ResponseResult<Object>)result;
            logOperation.setStatus(responseResult.getCode());
            if(responseResult.getCode() != 0){
                logOperation.setRemarks(responseResult.getMessage().toString());
            }
        } catch (Exception e) {
            if (response != null){
                if(response.getStatus() != 200){
                    logOperation.setStatus(-1);
             }
            }
        }
        ThreadUtil.execute(() -> logService.addLog(logOperation));
    }
}

package com.jmal.clouddisk.exception;

import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Arrays;
import java.util.List;


/**
 * 统一异常处理
 *
 * @author jmal
 */
@ControllerAdvice
@Slf4j
public class CommonExceptionHandler {

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseResult<Object> exceptionHandler(Exception e) {
        // 1. 定义需要忽略的异常类型
        final List<Class<? extends Exception>> IGNORED_EXCEPTIONS = Arrays.asList(
                AsyncRequestTimeoutException.class,
                ClientAbortException.class
        );

        // 2. 定义需要忽略的错误消息
        final List<String> IGNORED_MESSAGES = Arrays.asList(
                "Connection reset by peer",
                "Broken pipe"
        );

        // 3. 判断是否需要记录日志
        boolean shouldLog = IGNORED_EXCEPTIONS.stream().noneMatch(cls -> cls.isInstance(e));

        // 4. 检查异常类型

        // 5. 检查异常消息
        String errorMessage = e.getMessage();
        if (errorMessage != null && IGNORED_MESSAGES.stream().anyMatch(errorMessage::contains)) {
            shouldLog = false;
        }

        // 6. 记录日志
        if (shouldLog) {
            log.error("系统异常: {}", errorMessage, e);
        } else {
            log.debug("忽略的异常: {}", errorMessage);
        }

        // 7. 构建响应
        return ResultUtil.error(
                ExceptionType.SYSTEM_ERROR.getCode(),
                errorMessage != null ? errorMessage : "系统异常"
        );
    }



    @ExceptionHandler(CommonException.class)
    @ResponseBody
    public ResponseResult<Object> exceptionHandler(CommonException e) {
        if (e.getCode() == 0) {
            return ResultUtil.success(e.getData());
        }
        return ResultUtil.error(e.getCode(), e.getMsg());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseBody
    public ResponseResult<Object> exceptionHandler(MissingServletRequestParameterException e) {
        return ResultUtil.error(ExceptionType.MISSING_PARAMETERS.getCode(), "缺少参数%s".formatted(e.getParameterName()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    public ResponseResult<Object> exceptionHandler(MethodArgumentNotValidException e) {
        return ResultUtil.error(ExceptionType.MISSING_PARAMETERS.getCode(), e.getBindingResult().getAllErrors().get(0).getDefaultMessage());
    }

    @ExceptionHandler(BindException.class)
    @ResponseBody
    public ResponseResult<Object> exceptionHandler(BindException e) {
        return ResultUtil.error(ExceptionType.MISSING_PARAMETERS.getCode(), e.getBindingResult().getAllErrors().get(0).getDefaultMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseBody
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoResourceException() {
        return new ErrorResponseException(HttpStatus.NOT_FOUND);
    }
}

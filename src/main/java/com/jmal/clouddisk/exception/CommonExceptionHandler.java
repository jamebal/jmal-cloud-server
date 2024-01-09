package com.jmal.clouddisk.exception;

import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;


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
        if (e instanceof AsyncRequestTimeoutException || e instanceof ClientAbortException) {
            // ignore
        } else {
            log.error(e.getMessage(), e);
        }
        return ResultUtil.error(ExceptionType.SYSTEM_ERROR.getCode(), e.getMessage());
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
}

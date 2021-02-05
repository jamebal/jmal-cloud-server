package com.jmal.clouddisk.exception;

import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;


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
        log.error(e.getMessage(), e);
        return ResultUtil.error(ExceptionType.SYSTEM_ERROR.getCode(), e.getMessage());
    }

    @ExceptionHandler(CommonException.class)
    @ResponseBody
    public ResponseResult<Object> exceptionHandler(CommonException e) {
        return ResultUtil.error(e.getCode(), e.getMsg());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseBody
    public ResponseResult<Object> exceptionHandler(MissingServletRequestParameterException e) {
        return ResultUtil.error(ExceptionType.MISSING_PARAMETERS.getCode(), String.format("缺少参数%s", e.getParameterName()));
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

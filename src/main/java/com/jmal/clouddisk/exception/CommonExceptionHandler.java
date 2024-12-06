package com.jmal.clouddisk.exception;

import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.io.IOException;


/**
 * 统一异常处理
 *
 * @author jmal
 */
@ControllerAdvice
@Slf4j
public class CommonExceptionHandler {

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> handleIOException(IOException e) {
        if ("Broken pipe".equals(e.getMessage())) {
            // 不记录日志或仅记录简要信息
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        // 记录其他I/O异常
        log.error("Unhandled IOException: {}", e.getMessage(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal Server Error");
    }

    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public ResponseEntity<String> handleAsyncRequestTimeoutException() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @ExceptionHandler(ClientAbortException.class)
    public ResponseEntity<String> handleClientAbortException() {
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseResult<Object> exceptionHandler(Exception e) {
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

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseBody
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNoResourceException() {
        return new ErrorResponseException(HttpStatus.NOT_FOUND);
    }
}

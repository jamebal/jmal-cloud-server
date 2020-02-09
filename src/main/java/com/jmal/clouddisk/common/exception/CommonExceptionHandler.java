package com.jmal.clouddisk.common.exception;

import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletResponse;
import java.text.ParseException;

/**
 * CommonExceptionHandler
 *
 * @blame jmal
 */
@ControllerAdvice
public class CommonExceptionHandler {

    private Logger log = LoggerFactory.getLogger(CommonExceptionHandler.class);

    /**
     *  拦截Exception类的异常
     * @param e
     * @return
     */
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public ResponseResult exceptionHandler(Exception e){
        ResponseResult result;
        if(e instanceof CommonException) {
            result = ResultUtil.error(((CommonException) e).getCode(), ((CommonException) e).getMsg());
        }else if(e instanceof MissingServletRequestParameterException){
            result = ResultUtil.error(ExceptionType.MISSING_PARAMETERS.getCode(), String.format("缺少参数%s", ((MissingServletRequestParameterException) e).getParameterName()));
        }else if(e instanceof ParseException){
            result = ResultUtil.error(ExceptionType.UNPARSEABLE_DATE.getCode(),ExceptionType.UNPARSEABLE_DATE.getMsg());
        }else{
            // 接收到请求，记录请求内容
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletResponse response = attributes.getResponse();
                if(response != null) {
                    response.setStatus(400);
                }
            }
            log.error(e.getMessage(),e);
            result = ResultUtil.error(ExceptionType.SYSTEM_ERROR.getCode(), e.getMessage());
        }
        return result;
    }

    /**
     *  拦截CommonException类的异常
     * @param e
     * @return
     */
    @ExceptionHandler(CommonException.class)
    @ResponseBody
    public ResponseResult exceptionHandler(CommonException e){
        return ResultUtil.error(e.getCode(), e.getMsg());
    }
}

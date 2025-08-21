package com.jmal.clouddisk.util;

import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;

/**
 * ResultUtil
 *
 * @author jmal
 */
public class ResultUtil {

    private ResultUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static <T> ResponseResult<T> genResult() {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(0);
        result.setMessage("true");
        return result;
    }

    public static <T> ResponseResult<T> success(T data) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(0);
        result.setMessage("true");
        result.setData(data);
        return result;
    }

    public static <T> ResponseResult<T> successMsg(String message) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(0);
        result.setMessage(message);
        return result;
    }

    public static <T> ResponseResult<T> success() {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(0);
        result.setMessage("true");
        return result;
    }

    public static <T> ResponseResult<T> error(int code, String msg) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(code);
        result.setMessage(msg);
        return result;
    }

    public static <T> ResponseResult<T> error(String msg) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(-1);
        result.setMessage(msg);
        return result;
    }

    public static <T> ResponseResult<T> error(ExceptionType exceptionType) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(exceptionType.getCode());
        result.setMessage(exceptionType.getMsg());
        return result;
    }

    public static <T> ResponseResult<T> warning(String msg) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(-2);
        result.setMessage(msg);
        return result;
    }

    public static <T> ResponseResult<T> ignore(String msg) {
        ResponseResult<T> result = new ResponseResult<>();
        result.setCode(-3);
        result.setMessage(msg);
        return result;
    }

    /***
     * 检查参数是否为空
     * @param params 参数集合
     */
    public static void checkParamIsNull(Object... params) {
        for (Object param : params) {
            if (param instanceof String) {
                if ("".equals(param) || "null".equals(param)) {
                    throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
                }
            } else {
                if(param == null){
                    throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
                }
            }
        }
    }

}

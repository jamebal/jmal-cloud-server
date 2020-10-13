package com.jmal.clouddisk.util;

import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;

/**
 * ResultUtil
 *
 * @author jmal
 */
public class ResultUtil {

	public static ResponseResult<Object> genResult(){
		ResponseResult<Object> result = new ResponseResult<>();
		result.setCode(0);
		result.setMessage(true);
		return result;
	}

	public static <T> ResponseResult<Object> success(T data){
		ResponseResult<Object> result = new ResponseResult<>();
		result.setCode(0);
		result.setMessage(true);
		result.setData(data);
		return result;
	}

	public static <T> ResponseResult<Object> successMsg(String message){
		ResponseResult<Object> result = new ResponseResult<>();
		result.setCode(0);
		result.setMessage(message);
		return result;
	}

	public static <T> ResponseResult<Object> success(){
		ResponseResult<Object> result = new ResponseResult<>();
		result.setCode(0);
		result.setMessage(true);
		return result;
	}

	public static <T> ResponseResult<Object> error(int code, String msg){
		ResponseResult<Object> result = new ResponseResult<>();
		result.setCode(code);
		result.setMessage(msg);
		return result;
	}

	public static <T> ResponseResult<Object> error(String msg){
		ResponseResult<Object> result = new ResponseResult<>();
		result.setCode(-1);
		result.setMessage(msg);
		return result;
	}

	public static <T> ResponseResult<Object> warning(String msg){
		ResponseResult<Object> result = new ResponseResult<>();
		result.setCode(-2);
		result.setMessage(msg);
		return result;
	}

	/***
	 * 检查参数是否为空
	 * @param params
	 * @throws CommonException
	 */
	public static void checkParamIsNull(Object... params) throws CommonException {
		for (Object param : params) {
			if(param != null){
				if(param instanceof String){
					if(!"".equals(param) && !"null".equals(param)){
						continue;
					}
				}
				continue;
			}else{
				throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(),ExceptionType.MISSING_PARAMETERS.getMsg());
			}
		}
	}

}

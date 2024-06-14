package com.jmal.clouddisk.util;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.Map;

/**
 * ResponseResult
 *
 * @author jmal
 */
@Setter
@Getter
@Accessors(chain = true)
public class ResponseResult<T> {
	private int code;
	private Object message;
	private T data;
	private Object count;
	private Map<String, Object> props;
}

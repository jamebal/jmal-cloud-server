package com.jmal.clouddisk.util;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

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
	private T message;
	private T data;
	private T count;

}

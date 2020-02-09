package com.jmal.clouddisk.util;

import lombok.Getter;
import lombok.Setter;

/**
 * ResponseResult
 *
 * @blame jmal
 */
@Setter
@Getter
public class ResponseResult<T> {
	private int code;
	private T message;
	private T data;
	private T count;
}

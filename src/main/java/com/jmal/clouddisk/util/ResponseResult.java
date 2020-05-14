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

	public ResponseResult<T> error(int code,T msg){
		this.code = code;
		this.message = msg;
		return this;
	}

	public ResponseResult count(T count) {
		this.count = count;
		return this;
	}

	public ResponseResult message(T msg) {
		this.message = msg;
		return this;
	}

	public ResponseResult data(T data) {
		this.data = data;
		return this;
	}
}

package com.jmal.clouddisk.exception;

import org.springframework.data.annotation.Transient;

import java.io.Serial;

/**
 * @Description 通用异常处理类
 * @Author jmal
 * @Date 2019-08-15 10:54
 * @author jmal
 */
public class CommonException extends RuntimeException {

    /**
	 *
	 */
	@Serial
    private static final long serialVersionUID = 1L;
	private final int code;
    private final String msg;

    @Transient
    private final Object data;

    public CommonException(int code, String msg) {
        this.code = code;
        this.msg = msg;
        this.data = null;
    }

    public CommonException(ExceptionType type, Object data) {
        this.code = type.getCode();
        this.msg = type.getMsg();
        this.data = data;
    }

    public CommonException(ExceptionType type) {
        this.code = type.getCode();
        this.msg = type.getMsg();
        this.data = null;
    }

    public CommonException(String msg) {
        this.code = -1;
        this.msg = msg;
        this.data = null;
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    public Object getData() {
        return data;
    }

}

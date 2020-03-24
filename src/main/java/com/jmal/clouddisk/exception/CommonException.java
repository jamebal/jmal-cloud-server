package com.jmal.clouddisk.exception;

import org.springframework.util.StringUtils;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * @Description 通用异常处理类
 * @Author jmal
 * @Date 2019-08-15 10:54
 * @blame jmal
 */
public class CommonException extends RuntimeException {

    /**
	 *
	 */
	private static final long serialVersionUID = 1L;
	private final int code;
    private final String msg;

    public CommonException(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public CommonException(ExceptionType type) {
        this.code = type.getCode();
        this.msg = type.getMsg();
    }

    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    /***
     * 主要用于获取CompletableFuture中的异常,有异常则抛出
     * @param exception
     * @throws CommonException
     */
    public static void futureException(CompletableFuture<CommonException> exception) throws CommonException {
        if (exception.isDone()) {
            CommonException e = exception.join();
            if (e != null) {
                throw e;
            }
        }
    }

    @FunctionalInterface
    public interface ThrowingReturnFunction<T, R, E extends CommonException> {
        /***
         * 在stream中抛出异常
         * @param t
         * @return
         * @throws E
         */
        R apply(T t) throws E;

    }

    public static <T, R> Function<T, R> throwReturn(ThrowingReturnFunction<T, R, CommonException> throwingFunction) {
        return i -> {
            try {
                return throwingFunction.apply(i);
            } catch (CommonException e) {
                throw new CommonException(e.getCode(),e.getMsg());
            }
        };
    }

    /***
     * 检查参数
     * @param params
     * @throws CommonException
     */
    public static void checkParam(Object... params) throws CommonException {
        for (Object param : params) {
            if (StringUtils.isEmpty(param)) {
                throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), ExceptionType.MISSING_PARAMETERS.getMsg());
            }
        }
    }


}

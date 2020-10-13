package com.jmal.clouddisk.exception;

/**
 * @Description 异常枚举
 * @Date 2019-08-16 14:20
 * @author jmal
 */
public enum ExceptionType {

    /***
     * 其他异常
     */
    SYSTEM_ERROR(-1, "其他异常"),
    /***
     * 成功
     */
    SYSTEM_SUCCESS(0, "true"),
    /***
     * 缺少参数
     */
    MISSING_PARAMETERS(1, "缺少参数"),
    /***
     * 时间格式不正确
     */
    UNPARSEABLE_DATE(2, "时间格式不正确"),
    /***
     * 缺少Header
     */
    MISSING_HEADERTERS(3, "缺少Header"),
    /***
     * 该资源已存在
     */
    EXISTING_RESOURCES(4, "该资源已存在"),
    /***
     * 未登录或登录超时
     */
    LOGIN_EXCEPRION(5, "未登录或登录超时"),
    /***
     * 本地系统上传数据到平台的问题
     */
    UPLOAD_LSC_EXCEPRION(6, "本地系统上传数据到平台的问题"),

    /***
     * 文件不存在
     */
    FILE_NOT_FIND(7, "文件不存在"),

    /**
     * 没有权限
     */
    PERMISSION_DENIED(8, "没有权限"),

    /**
     * 自定义异常
     */
    CUSTOM_EXCEPTION(9, "自定义异常"),

    /**
     * 参数值不对
     */
    PARAMETERS_VALUE(10, "参数值不对"),

    /**
     * 参数值不对
     */
    OFFLINE(11, "离线"),

    /**
     * 用户不存在
     */
    USER_NOT_FIND(12, "用户不存在"),

    /***
     * 目录不存在
     */
    DIR_NOT_FIND(13, "目录不存在"),

    /***
     * 解压失败
     */
    FAIL_DECOMPRESS(14, "解压失败"),

    /***
     * 无法识别的文件
     */
    UNRECOGNIZED_FILE(15, "无法识别的文件"),

    /***
     * 合并文件失败
     */
    FAIL_MERGA_FILE(16, "合并文件失败"),

    /***
     * 删除文件失败
     */
    FAIL_DELETE_FILE(17, "删除文件失败");


    public int getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }

    private int code;
    private String msg;

    ExceptionType(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}

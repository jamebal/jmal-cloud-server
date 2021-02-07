package com.jmal.clouddisk.annotation;

import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * LogOperation
 * @author jmal
 */
@Data
@Document(collection = "log")
public class LogOperation {
    private String id;
    /***
     * 账号
     */
    private String username;
    /***
     * 用户名
     */
    private String showName;
    /***
     * ip地址
     */
    private String ip;
    /***
     * 操作模块
     */
    private String operationModule;
    /***
     * 操作功能
     */
    private String operationFun;
    /***
     * 请求地址
     */
    private String url;
    /***
     * 请求方式
     */
    private String method;
    /***
     * 设备型号
     */
    private String deviceModel;
    /***
     * 操作系统
     */
    private String operatingSystem;
    /***
     * 耗时
     */
    private Long time;
    /***
     * 状态
     */
    private Integer status;
    /***
     * 操作时间
     */
    private LocalDateTime createTime;
    /***
     * 备注
     */
    private String remarks;
    /***
     * 日志类型
     */
    private String type;

    /***
     * 日志类型
     */
    public static enum Type {
        /***
         * 登陆日志
         */
        LOGIN,
        /***
         * 操作日志
         */
        OPERATION
    }
}

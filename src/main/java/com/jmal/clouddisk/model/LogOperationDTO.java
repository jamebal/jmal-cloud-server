package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author jmal
 * @Description 操作日志传输对象
 * @Date 2021/2/8 2:16 下午
 */
@Data
@Schema
public class LogOperationDTO {
    /***
     * 页数
     */
    @Schema(name = "page", title = "页数", example = "1")
    private Integer page;
    /**
     * 每页条数
     */
    @Schema(name = "pageSize", title = "每页条数", example = "10")
    private Integer pageSize;
    /**
     * 账号
     */
    private String username;
    /**
     * 用户名
     */
    private String showName;
    /**
     * avatar
     */
    private String avatar;
    /**
     * 排除账号
     */
    private String excludeUsername;
    /**
     * ip
     */
    private String ip;
    /**
     * 日志类型
     */
    private String type;
    /**
     * 要查询的排序参数
     */
    private String sortProp;
    /**
     * 要查询的排序顺序
     */
    private String sortOrder;
    /**
     * 开始时间
     */
    private Long startTime;
    /**
     * 结束时间
     */
    private Long endTime;

    /**
     * 操作时间
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy年MM月dd日 HH:mm:ss")
    private LocalDateTime createTime;

    /**
     * 操作功能
     */
    private String operationFun;
}

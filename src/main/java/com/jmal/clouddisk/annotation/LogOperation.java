package com.jmal.clouddisk.annotation;

import lombok.Data;

import java.util.Map;

/**
 * LogOperation
 *
 * @blame jmal
 */
@Data
public class LogOperation {
    private String id;
    private int projectId;
    private String userName;
    private String logInfo;
    private String url;
    private long time;
    private String ip;
    private String createTime;
    private long timeMillis;
    private Map<String, Object> props;
}

package com.jmal.clouddisk.lucene;

import lombok.Data;

@Data
public class TaskProgress {
    /**
     * 任务id
     */
    private String taskId;
    /**
     * 任务类型
     */
    private TaskType taskType;
    /**
     * 任务类型 taskType.getType()
     */
    private String type;
    /**
     * 任务名称
     */
    private String name;
    /**
     * 文件路径
     */
    private String path;
    /**
     * 任务状态
     */
    private String progress;
    private String username;

    public TaskProgress(String taskId, String username, TaskType taskType, String name, String progress) {
        this.taskId = taskId;
        this.username = username;
        this.taskType = taskType;
        this.type = taskType.getType();
        this.name = name;
        this.progress = progress;
    }

}

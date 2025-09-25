package com.jmal.clouddisk.service.impl;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class FileVersionEvent extends ApplicationEvent {

    private final String fileUsername;
    private final String userId;
    private final String path;
    private final String operator;

    public FileVersionEvent(Object source, String fileUsername, String path, String userId, String operator) {
        super(source);
        this.fileUsername = fileUsername;
        this.path = path;
        this.userId = userId;
        this.operator = operator;
    }

}

package com.jmal.clouddisk.lucene;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class RebuildIndexEvent extends ApplicationEvent {

    private final String username;

    private final String fileAbsolutePath;

    public RebuildIndexEvent(Object source, String username, String fileAbsolutePath) {
        super(source);
        this.username = username;
        this.fileAbsolutePath = fileAbsolutePath;
    }

}

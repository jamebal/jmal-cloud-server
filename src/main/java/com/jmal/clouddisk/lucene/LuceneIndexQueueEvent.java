package com.jmal.clouddisk.lucene;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

@Getter
public class LuceneIndexQueueEvent extends ApplicationEvent {

    private final String fileId;

    private final List<String> delFileIds;

    public LuceneIndexQueueEvent(Object source, String fileId) {
        super(source);
        this.fileId = fileId;
        this.delFileIds = null;
    }

    public LuceneIndexQueueEvent(Object source, List<String> delFileIds) {
        super(source);
        this.fileId = null;
        this.delFileIds = delFileIds;
    }

}

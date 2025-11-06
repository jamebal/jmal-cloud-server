package com.jmal.clouddisk.lucene;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Collection;

@Getter
public class LuceneIndexQueueEvent extends ApplicationEvent {

    private final String fileId;

    private final Collection<String> delFileIds;

    public LuceneIndexQueueEvent(Object source, String fileId) {
        super(source);
        this.fileId = fileId;
        this.delFileIds = null;
    }

    public LuceneIndexQueueEvent(Object source, Collection<String> delFileIds) {
        super(source);
        this.fileId = null;
        this.delFileIds = delFileIds;
    }

}

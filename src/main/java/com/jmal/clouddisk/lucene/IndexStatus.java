package com.jmal.clouddisk.lucene;

import lombok.Getter;

/**
 * 索引状态
 */
@Getter
public enum IndexStatus {
    /**
     * 未索引,待索引
     */
    NOT_INDEX(0),
    /**
     * 正在进行索引
     */
    INDEXING(1),
    /**
     * 已完成索引
     */
    INDEXED(2);

    private final int status;

    IndexStatus(int status) {
        this.status = status;
    }

}

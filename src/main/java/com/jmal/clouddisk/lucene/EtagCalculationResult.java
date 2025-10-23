package com.jmal.clouddisk.lucene;

public enum EtagCalculationResult {
    UPDATED,          // ETag已更新
    NOT_CHANGED,      // ETag未发生变化
    SKIPPED_CHILD_NULL, // 因检测到子项ETag为null而跳过
    ERROR             // 发生其他错误
}

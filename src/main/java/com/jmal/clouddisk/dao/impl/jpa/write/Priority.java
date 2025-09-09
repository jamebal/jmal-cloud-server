package com.jmal.clouddisk.dao.impl.jpa.write;

/**
 * 操作任务的优先级
 */
public enum Priority {
    /**
     * 高优先级 - 例如用户直接触发的、需要立即响应的操作
     */
    HIGH,
    /**
     * 普通优先级 - 例如常规的后台任务
     */
    NORMAL,
    /**
     * 最低优先级 - 仅用于内部信号，如关闭信号（毒丸）
     */
    LOWEST
}

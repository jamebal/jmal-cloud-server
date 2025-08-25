package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@Builder
public class OperationTips implements Reflective {
    /**
     * 操作结果说明
     */
    String msg;
    /**
     * 操作结果状态
     */
    Boolean success;
    /**
     * 操作
     */
    String operation;
}

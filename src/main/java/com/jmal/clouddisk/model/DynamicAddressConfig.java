package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DynamicAddressConfig {

    /**
     * 是否启用动态地址复制能力
     */
    private Boolean enabled;

    /**
     * 可选域名；为空时前端直接使用 STUN 上报的 host:port
     */
    private String domain;
}

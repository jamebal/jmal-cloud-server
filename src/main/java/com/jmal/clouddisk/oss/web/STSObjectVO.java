package com.jmal.clouddisk.oss.web;

import lombok.Data;

/**
 * @author jmal
 * @Description STS
 * @date 2023/4/4 16:00
 */
@Data
public class STSObjectVO {
    private String accessKeyId;
    private String accessKeySecret;
    private String securityToken;
    private String expiration;
}

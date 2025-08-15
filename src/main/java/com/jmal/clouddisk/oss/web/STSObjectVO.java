package com.jmal.clouddisk.oss.web;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;

/**
 * @author jmal
 * @Description STS
 * @date 2023/4/4 16:00
 */
@Data
public class STSObjectVO implements Reflective {
    private String accessKeyId;
    private String accessKeySecret;
    private String securityToken;
    private String expiration;
}

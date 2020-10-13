package com.jmal.clouddisk.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Description userToken
 * @Author jmal
 * @Date 2020/9/30 10:34 上午
 */
@Data
@Accessors(chain = true)
public class UserToken {
    private String usernmae;
    private long timestamp;
}

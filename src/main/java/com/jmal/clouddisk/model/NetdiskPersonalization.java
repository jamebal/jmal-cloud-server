package com.jmal.clouddisk.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NetdiskPersonalization {

    private String name;

    /**
     * 登录页背景图片URL
     */
    private String loginBackgroundUrl;

    /**
     * 登录页背景图片模糊度(0~20)
     */
    private Integer loginBackgroundBlur;

}

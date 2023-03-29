package com.jmal.clouddisk.oss;

import lombok.Getter;

/**
 * @author jmal
 * @Description platform
 * @date 2023/3/29 14:00
 */
public enum PlatformOSS {
    ALIYUN("aliyunOss", "阿里云OSS"),
    TENCENT("tencentOss", "腾讯云OSS");

    @Getter
    private final String key;
    @Getter
    private final String value;

    PlatformOSS(String key, String value) {
        this.key = key;
        this.value = value;
    }
}

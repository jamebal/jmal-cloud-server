package com.jmal.clouddisk.oss;

import lombok.Getter;

/**
 * @author jmal
 * @Description platform
 * @date 2023/3/29 14:00
 */
public enum PlatformOSS {
    ALIYUN("aliyun", "阿里云OSS"),
    TENCENT("tencent", "腾讯云COS"),
    MINIO("minio", "S3兼容");

    @Getter
    private final String key;
    @Getter
    private final String value;

    PlatformOSS(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public static PlatformOSS getPlatform(String key) {
        for (PlatformOSS platformOSS : values()) {
            if (platformOSS.getKey().equals(key)) {
                return platformOSS;
            }
        }
        return ALIYUN;
    }

}

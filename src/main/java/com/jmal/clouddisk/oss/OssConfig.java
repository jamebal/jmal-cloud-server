package com.jmal.clouddisk.oss;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.oss.aliyun.AliyunOssService;
import com.jmal.clouddisk.oss.tencent.TencentOssService;
import com.jmal.clouddisk.util.CaffeineUtil;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jmal
 * @Description OssConfig
 * @date 2023/4/4 15:13
 */
@Component
public class OssConfig {

    private static final Map<String, IOssService> OSS_SERVICE_MAP = new ConcurrentHashMap<>();

    public OssConfig(FileProperties fileProperties) {
        // TODO 临时插入
        BucketInfo bucketInfo = new BucketInfo();
        bucketInfo.setPlatform(PlatformOSS.ALIYUN);
        bucketInfo.setBucketName("jmalcloud");
        bucketInfo.setUsername("jmal");
        bucketInfo.setFolderName("aliyunoss");
        CaffeineUtil.setOssDiameterPrefixCache("/jmal/aliyunoss", bucketInfo);
        IOssService ossService1 = new AliyunOssService(fileProperties);
        OSS_SERVICE_MAP.put(ossService1.getPlatform().getKey(), ossService1);

        BucketInfo bucketInfo1 = new BucketInfo();
        bucketInfo1.setPlatform(PlatformOSS.TENCENT);
        bucketInfo1.setBucketName("test-1303879235");
        bucketInfo1.setUsername("jmal");
        bucketInfo1.setFolderName("tencentoss");
        CaffeineUtil.setOssDiameterPrefixCache("/jmal/tencentoss", bucketInfo1);
        IOssService ossService2 = new TencentOssService(fileProperties);
        OSS_SERVICE_MAP.put(ossService2.getPlatform().getKey(), ossService2);
    }

    public static Map<String, IOssService> getOssServiceMap() {
        return OSS_SERVICE_MAP;
    }

    @PreDestroy
    public void destroy() {
        getOssServiceMap().values().forEach(IOssService::close);
    }
}

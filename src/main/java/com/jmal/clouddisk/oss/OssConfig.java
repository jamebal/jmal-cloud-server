package com.jmal.clouddisk.oss;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.oss.aliyun.AliyunOssService;
import com.jmal.clouddisk.oss.tencent.TencentOssService;
import com.jmal.clouddisk.oss.web.model.OssConfigDO;
import com.jmal.clouddisk.oss.web.model.OssConfigDTO;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import jakarta.annotation.PreDestroy;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jmal
 * @Description OssConfig
 * @date 2023/4/4 15:13
 */
@Component
public class OssConfig {

    public static final String COLLECTION_NAME = "OssConfig";
    private static final Map<String, IOssService> OSS_SERVICE_MAP = new ConcurrentHashMap<>();

    private final UserServiceImpl userService;

    private final MongoTemplate mongoTemplate;

    public OssConfig(FileProperties fileProperties, UserServiceImpl userService, MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        // load config
        List<OssConfigDO> ossConfigDOList = mongoTemplate.findAll(OssConfigDO.class);
        for (OssConfigDO ossConfigDO : ossConfigDOList) {
            String userId = ossConfigDO.getUserId();
            ConsumerDO consumerDO = userService.userInfoById(userId);
            if (consumerDO == null) {
                continue;
            }
            OssConfigDTO ossConfigDTO = ossConfigDO.toOssConfigDTO(userService, userService.userInfoById(userId));
            BucketInfo bucketInfo = new BucketInfo();
            bucketInfo.setPlatform(ossConfigDO.getPlatform());
            bucketInfo.setBucketName(ossConfigDTO.getBucket());
            bucketInfo.setUsername(consumerDO.getUsername());
            bucketInfo.setFolderName(ossConfigDTO.getFolderName());
            ossConfigDTO.setUsername(consumerDO.getUsername());
            CaffeineUtil.setOssDiameterPrefixCache(bucketInfo.getWebPathPrefix(), bucketInfo);
            IOssService ossService = getOssService(fileProperties, ossConfigDO.getPlatform(), ossConfigDTO);
            if (ossService != null) {
                OSS_SERVICE_MAP.put(ossService.getPlatform().getKey(), ossService);
            }
        }
        this.userService = userService;
    }

    private static IOssService getOssService(FileProperties fileProperties, PlatformOSS platformOSS, OssConfigDTO ossConfigDTO) {
        IOssService ossService = null;
        switch (platformOSS) {
            case ALIYUN -> ossService = new AliyunOssService(fileProperties, ossConfigDTO);
            case TENCENT -> ossService = new TencentOssService(fileProperties, ossConfigDTO);
        }
        return ossService;
    }

    public static Map<String, IOssService> getOssServiceMap() {
        return OSS_SERVICE_MAP;
    }

    @PreDestroy
    public void destroy() {
        getOssServiceMap().values().forEach(IOssService::close);
    }

    public ResponseResult<Object> addOssConfig(OssConfigDTO ossConfigDTO) {
        String userId = ossConfigDTO.getUserId();
        ConsumerDO consumerDO = userService.getUserInfoById(userId);
        OssConfigDO ossConfigDO = ossConfigDTO.toOssConfigDO(consumerDO.getPassword());
        mongoTemplate.save(ossConfigDO);
        return ResultUtil.success();
    }
}

package com.jmal.clouddisk.oss;

import cn.hutool.core.io.file.PathUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.exception.ExceptionType;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jmal
 * @Description OssConfig
 * @date 2023/4/4 15:13
 */
@Component
public class OssConfigService {

    public static final String COLLECTION_NAME = "OssConfig";
    private static final Map<String, IOssService> OSS_SERVICE_MAP = new ConcurrentHashMap<>();

    private final UserServiceImpl userService;

    private final FileProperties fileProperties;

    private final MongoTemplate mongoTemplate;

    public OssConfigService(FileProperties fileProperties, UserServiceImpl userService, MongoTemplate mongoTemplate) {
        // load config
        List<OssConfigDO> ossConfigDOList = mongoTemplate.findAll(OssConfigDO.class);
        for (OssConfigDO ossConfigDO : ossConfigDOList) {
            String userId = ossConfigDO.getUserId();
            ConsumerDO consumerDO = userService.userInfoById(userId);
            if (consumerDO == null) {
                continue;
            }
            OssConfigDTO ossConfigDTO = ossConfigDO.toOssConfigDTO(userService, userService.userInfoById(userId));
            ossConfigDTO.setUsername(consumerDO.getUsername());
            setBucketInfoCache(ossConfigDO, ossConfigDTO);
            IOssService ossService = getOssService(fileProperties, ossConfigDO.getPlatform(), ossConfigDTO);
            if (ossService != null) {
                setOssServiceMap(ossConfigDO.getPlatform().getKey(), ossService);
            }
        }
        this.userService = userService;
        this.mongoTemplate = mongoTemplate;
        this.fileProperties = fileProperties;
    }

    private static void setBucketInfoCache(OssConfigDO ossConfigDO, OssConfigDTO ossConfigDTO) {
        BucketInfo bucketInfo = new BucketInfo();
        bucketInfo.setPlatform(ossConfigDO.getPlatform());
        bucketInfo.setBucketName(ossConfigDTO.getBucket());
        bucketInfo.setUsername(ossConfigDTO.getUsername());
        bucketInfo.setFolderName(ossConfigDTO.getFolderName());
        CaffeineUtil.setOssDiameterPrefixCache(bucketInfo.getWebPathPrefix(), bucketInfo);
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

    public static void setOssServiceMap(String platformKey, IOssService ossService) {
        OSS_SERVICE_MAP.put(platformKey, ossService);
    }

    @PreDestroy
    public void destroy() {
        getOssServiceMap().values().forEach(IOssService::close);
    }

    public ResponseResult<Object> putOssConfig(OssConfigDTO ossConfigDTO) {
        IOssService ossService;
        String userId = ossConfigDTO.getUserId();
        ConsumerDO consumerDO = userService.getUserInfoById(userId);
        if (consumerDO == null) {
            return ResultUtil.error(ExceptionType.PARAMETERS_VALUE.getCode(), "无效参数 userId");
        }
        ossConfigDTO.setUsername(consumerDO.getUsername());
        OssConfigDO ossConfigDO = ossConfigDTO.toOssConfigDO(consumerDO.getPassword());
        String configErr = "配置有误";
        try {
            // 检查配置可用性
            ossService = getOssService(fileProperties, ossConfigDO.getPlatform(), ossConfigDTO);
            if (ossService == null) {
                return ResultUtil.warning(configErr);
            }
            boolean doesBucketExist = ossService.doesBucketExist();
            if (!doesBucketExist) {
                return ResultUtil.warning("Bucket 不存在");
            } else {
                setOssConfig(ossConfigDTO, ossService, userId, ossConfigDO);
            }
        } catch (Exception e) {
            return ResultUtil.warning(configErr);
        }
        return ResultUtil.success();
    }

    private void setOssConfig(OssConfigDTO ossConfigDTO, IOssService ossService, String userId, OssConfigDO ossConfigDO) {
        // mkdir
        Path path = Paths.get(fileProperties.getRootDir(), ossConfigDTO.getUsername(), ossConfigDTO.getFolderName());
        PathUtil.mkdir(path);
        setBucketInfoCache(ossConfigDO, ossConfigDTO);
        setOssServiceMap(ossConfigDO.getPlatform().getKey(), ossService);
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("bucket").is(ossConfigDTO.getBucket()));
        query.addCriteria(Criteria.where("platform").is(ossConfigDO.getPlatform()));
        Update update = new Update();
        update.set("platform", ossConfigDO.getPlatform());
        update.set("folderName", ossConfigDO.getFolderName());
        if (!ossConfigDTO.getAccessKey().contains("*")) {
            update.set("accessKey", ossConfigDO.getAccessKey());
        }
        if (!ossConfigDTO.getSecretKey().contains("*")) {
            update.set("secretKey", ossConfigDO.getSecretKey());
        }
        update.set("endpoint", ossConfigDO.getEndpoint());
        update.set("region", ossConfigDO.getRegion());
        update.set("bucket", ossConfigDO.getBucket());
        update.set("userId", ossConfigDO.getUserId());
        mongoTemplate.upsert(query, update, COLLECTION_NAME);
    }

    /**
     * 判断目录是否存在
     *
     * @param username   username
     * @param folderName 目录名
     */
    public ResponseResult<Boolean> existFolderName(String username, String folderName) {
        Path path = Paths.get(fileProperties.getRootDir(), username, folderName);
        return ResultUtil.success(PathUtil.exists(path, false));
    }
}

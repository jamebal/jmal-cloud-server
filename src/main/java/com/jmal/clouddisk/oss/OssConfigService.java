package com.jmal.clouddisk.oss;

import cn.hutool.core.io.file.PathUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.listener.FileMonitor;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.oss.aliyun.AliyunOssService;
import com.jmal.clouddisk.oss.tencent.TencentOssService;
import com.jmal.clouddisk.oss.web.model.OssConfigDO;
import com.jmal.clouddisk.oss.web.model.OssConfigDTO;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.webdav.MyWebdavServlet;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

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
@Service
@Slf4j
public class OssConfigService {

    public static final String COLLECTION_NAME = "OssConfig";
    private static final Map<String, IOssService> OSS_SERVICE_MAP = new ConcurrentHashMap<>();
    private final UserServiceImpl userService;

    private final FileProperties fileProperties;

    private final FileMonitor fileMonitor;

    private final MongoTemplate mongoTemplate;

    public OssConfigService(FileProperties fileProperties, UserServiceImpl userService, MongoTemplate mongoTemplate, FileMonitor fileMonitor) {
        this.userService = userService;
        this.mongoTemplate = mongoTemplate;
        this.fileProperties = fileProperties;
        this.fileMonitor = fileMonitor;
    }

    @PostConstruct
    public void init() {
        // load config
        List<OssConfigDO> ossConfigDOList = mongoTemplate.findAll(OssConfigDO.class);
        for (OssConfigDO ossConfigDO : ossConfigDOList) {
            String userId = ossConfigDO.getUserId();
            ConsumerDO consumerDO = userService.userInfoById(userId);
            if (consumerDO == null) {
                continue;
            }
            OssConfigDTO ossConfigDTO = ossConfigDO.toOssConfigDTO(userService.userInfoById(userId));
            ossConfigDTO.setUsername(consumerDO.getUsername());
            IOssService ossService = null;
            try {
                ossService = newOssService(fileProperties, ossConfigDO.getPlatform(), ossConfigDTO);
                if (ossService != null) {
                    setBucketInfoCache(ossConfigDO.getPlatform(), ossConfigDTO, ossService);
                }
            } catch (Exception e) {
                log.error(ossConfigDO.getPlatform().getValue() + " 配置加载失败!");
                log.error(e.getMessage(), e);
                if (ossService != null) {
                    ossService.close();
                }
            }
        }
    }

    public void setOssServiceMap(String key, IOssService ossService) {
        OSS_SERVICE_MAP.put(key, ossService);
    }

    public static IOssService getOssStorageService(String ossPath) {
        BucketInfo bucketInfo = CaffeineUtil.getOssDiameterPrefixCache(ossPath);
        return OssConfigService.getOssService(bucketInfo.getWebPathPrefix());
    }

    public static String getObjectName(String path, String ossPath) {
        Path prePath = Paths.get(path);
        String name = "";
        if (prePath.getNameCount() > 2) {
            name = path.substring(ossPath.length() + 1);
            if (!name.endsWith("/")) {
                name = name + "/";
            }
        }
        return name;
    }

    public static IOssService getOssService(String key) {
        return OSS_SERVICE_MAP.get(key);
    }

    private void setBucketInfoCache(PlatformOSS platformOSS, OssConfigDTO ossConfigDTO, IOssService ossService) {
        BucketInfo bucketInfo = new BucketInfo();
        bucketInfo.setPlatform(platformOSS);
        bucketInfo.setBucketName(ossConfigDTO.getBucket());
        bucketInfo.setUsername(ossConfigDTO.getUsername());
        bucketInfo.setFolderName(ossConfigDTO.getFolderName());
        String webPathPrefix = bucketInfo.getWebPathPrefix();
        // 销毁掉之前的IOssService
        destroyOssService(webPathPrefix);
        // setOssDiameterPrefixCache
        CaffeineUtil.setOssDiameterPrefixCache(webPathPrefix, bucketInfo);
        // setOssService
        setOssServiceMap(webPathPrefix, ossService);
        fileMonitor.addDirFilter(webPathPrefix);
    }

    private void destroyOssService(String key) {
        CaffeineUtil.removeOssDiameterPrefixCache(key);
        if (OSS_SERVICE_MAP.containsKey(key)) {
            // 销毁掉之前的IOssService
            getOssService(key).close();
        }
        OSS_SERVICE_MAP.remove(key);
        // 移除webPathPrefix的文件夹的 过滤器
        fileMonitor.removeDirFilter(key);
    }

    /**
     * 创建IOssService对象
     * @param fileProperties  FileProperties
     * @param platformOSS     PlatformOSS
     * @param ossConfigDTO    OssConfigDTO
     * @return IOssService 对象
     */
    private static IOssService newOssService(FileProperties fileProperties, PlatformOSS platformOSS, OssConfigDTO ossConfigDTO) {
        IOssService ossService = null;
        switch (platformOSS) {
            case ALIYUN -> ossService = new AliyunOssService(fileProperties, ossConfigDTO);
            case TENCENT -> ossService = new TencentOssService(fileProperties, ossConfigDTO);
        }
        return ossService;
    }

    @PreDestroy
    public void destroy() {
        OSS_SERVICE_MAP.values().forEach(IOssService::close);
    }

    public void putOssConfig(OssConfigDTO ossConfigDTO) {
        IOssService ossService = null;
        String userId = ossConfigDTO.getUserId();
        ConsumerDO consumerDO = userService.getUserInfoById(userId);
        if (consumerDO == null) {
            throw new CommonException(ExceptionType.PARAMETERS_VALUE.getCode(), "无效参数 userId");
        }
        ossConfigDTO.setUsername(consumerDO.getUsername());
        Query query = new Query();
        // 销毁旧配置
        destroyOldConfig(ossConfigDTO, userId, consumerDO, query);

        // 配置转换 DTO -> DO
        OssConfigDO ossConfigDO = ossConfigDTO.toOssConfigDO(consumerDO.getPassword());

        String configErr = "配置有误 或者 Access Key 没有权限";
        try {
            // 检查配置可用性
            ossService = newOssService(fileProperties, ossConfigDO.getPlatform(), ossConfigDTO);
            if (ossService == null) {
                throw new CommonException(ExceptionType.WARNING.getCode(), configErr);
            }
            boolean doesBucketExist = ossService.doesBucketExist();
            if (!doesBucketExist) {
                ossService.close();
                throw new CommonException(ExceptionType.WARNING.getCode(), "Bucket 不存在");
            } else {
                // 更新配置
                updateOssConfig(ossConfigDTO, ossService, query, ossConfigDO);
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
            if (ossService != null) {
                ossService.close();
            }
            throw new CommonException(ExceptionType.WARNING.getCode(), configErr);
        }
    }

    /**
     * 销毁旧配置
     */
    private void destroyOldConfig(OssConfigDTO ossConfigDTO, String userId, ConsumerDO consumerDO, Query query) {
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("bucket").is(ossConfigDTO.getBucket()));
        query.addCriteria(Criteria.where("platform").is(PlatformOSS.getPlatform(ossConfigDTO.getPlatform())));
        // 检查目录是否存在
        boolean existFolder = existFolderName(consumerDO.getUsername(), ossConfigDTO.getFolderName());
        // 旧配置
        OssConfigDO oldOssConfigDO = mongoTemplate.findOne(query, OssConfigDO.class);
        if (oldOssConfigDO != null) {
            if (!oldOssConfigDO.getFolderName().equals(ossConfigDTO.getFolderName())) {
                // 修改了目录名
                Path oldPath = Paths.get(fileProperties.getRootDir(), ossConfigDTO.getUsername(), oldOssConfigDO.getFolderName());
                PathUtil.del(oldPath);
                if (existFolder) {
                    throw new CommonException(ExceptionType.WARNING.getCode(), "目录已存在: " + ossConfigDTO.getFolderName());
                }
            }
            // 销毁 old OssService
            destroyOssService(MyWebdavServlet.getPathDelimiter(ossConfigDTO.getUsername(), oldOssConfigDO.getFolderName()));
            if (ossConfigDTO.getAccessKey().contains("*")) {
                ossConfigDTO.setAccessKey(UserServiceImpl.getDecryptStrByUser(oldOssConfigDO.getAccessKey(), consumerDO));
            }
            if (ossConfigDTO.getSecretKey().contains("*")) {
                ossConfigDTO.setSecretKey(UserServiceImpl.getDecryptStrByUser(oldOssConfigDO.getSecretKey(), consumerDO));
            }
        }
        if (existFolder && oldOssConfigDO == null) {
            throw new CommonException(ExceptionType.WARNING.getCode(), "目录已存在: " + ossConfigDTO.getFolderName());
        }

    }

    /**
     * 更新配置
     */
    private void updateOssConfig(OssConfigDTO ossConfigDTO, IOssService ossService, Query query, OssConfigDO ossConfigDO) {
        // mkdir
        Path path = Paths.get(fileProperties.getRootDir(), ossConfigDTO.getUsername(), ossConfigDTO.getFolderName());
        PathUtil.mkdir(path);
        setBucketInfoCache(ossConfigDO.getPlatform(), ossConfigDTO, ossService);
        Update update = new Update();
        update.set("platform", ossConfigDO.getPlatform());
        update.set("folderName", ossConfigDO.getFolderName());
        update.set("accessKey", ossConfigDO.getAccessKey());
        update.set("secretKey", ossConfigDO.getSecretKey());
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
    private boolean existFolderName(String username, String folderName) {
        Path path = Paths.get(fileProperties.getRootDir(), username, folderName);
        return PathUtil.exists(path, false);
    }

    /**
     * OSS配置列表
     */
    public ResponseResult<Object> ossConfigList() {
        List<OssConfigDO> ossConfigDOList = mongoTemplate.findAll(OssConfigDO.class);
        List<OssConfigDTO> ossConfigDTOList = ossConfigDOList.stream().map(OssConfigDO::toOssConfigDTO).toList();
        return ResultUtil.success(ossConfigDTOList);
    }

    /**
     * 删除OSS配置
     * @param id ossConfigId
     */
    public ResponseResult<Object> deleteOssConfig(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        OssConfigDO ossConfigDO = mongoTemplate.findAndRemove(query, OssConfigDO.class);
        if (ossConfigDO != null) {
            // 销毁IOssService
            String key = MyWebdavServlet.getPathDelimiter(userService.getUserNameById(ossConfigDO.getUserId()), ossConfigDO.getFolderName());
            destroyOssService(key);
        }
        return ResultUtil.success();
    }
}

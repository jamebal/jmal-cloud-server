package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.oss.PlatformOSS;
import com.jmal.clouddisk.oss.web.model.OssConfigDO;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public interface IOssConfigDAO {

    List<OssConfigDO> findAll();

    OssConfigDO findByUserIdAndEndpointAndBucketAndPlatform(String userId, @NotNull(message = "endpoint 不能为空") String endpoint, @NotNull(message = "bucket 不能为空") String bucket, PlatformOSS platform);

    void updateOssConfigBy(OssConfigDO ossConfigDO);

    OssConfigDO findAndRemoveById(String id);
}

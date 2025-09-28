package com.jmal.clouddisk.dao.repository.jpa;

import com.jmal.clouddisk.oss.PlatformOSS;
import com.jmal.clouddisk.oss.web.model.OssConfigDO;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OssConfigRepository extends JpaRepository<OssConfigDO, String> {

    OssConfigDO findByUserIdAndEndpointAndBucketAndPlatform(String userId, String endpoint, String bucket, PlatformOSS platform);

    List<OssConfigDO> findAllByUserId(String userId);
}

package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IOssConfigDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.OssConfigRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.ossconfig.OssConfigOperation;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.oss.PlatformOSS;
import com.jmal.clouddisk.oss.web.model.OssConfigDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class OssConfigDAOJpaImpl implements IOssConfigDAO, IWriteCommon<OssConfigDO> {

    private final OssConfigRepository ossConfigRepository;

    private final IWriteService writeService;

    @Override
    public void AsyncSaveAll(Iterable<OssConfigDO> entities) {
        writeService.submit(new OssConfigOperation.CreateAll(entities));
    }

    @Override
    public List<OssConfigDO> findAll() {
        return ossConfigRepository.findAll();
    }

    @Override
    public OssConfigDO findByUserIdAndEndpointAndBucketAndPlatform(String userId, String endpoint, String bucket, PlatformOSS platform) {
        return ossConfigRepository.findByUserIdAndEndpointAndBucketAndPlatform(userId, endpoint, bucket, platform);
    }

    @Override
    public void updateOssConfigBy(OssConfigDO ossConfigDO) {
        try {
            writeService.submit(new OssConfigOperation.CreateAll(List.of(ossConfigDO))).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public OssConfigDO findAndRemoveById(String id) {
        OssConfigDO ossConfigDO = ossConfigRepository.findById(id).orElse(null);
        if (ossConfigDO != null) {
            try {
                writeService.submit(new OssConfigOperation.DeleteById(id)).get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new CommonException(e.getMessage());
            }
        }
        return ossConfigDO;
    }
}

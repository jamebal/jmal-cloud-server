package com.jmal.clouddisk.dao.impl.mongodb;

import com.jmal.clouddisk.dao.IOssConfigDAO;
import com.jmal.clouddisk.oss.PlatformOSS;
import com.jmal.clouddisk.oss.web.model.OssConfigDO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class OssConfigDAOImpl implements IOssConfigDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<OssConfigDO> findAll() {
        return mongoTemplate.findAll(OssConfigDO.class);
    }

    @Override
    public List<OssConfigDO> findAllByUserId(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        return mongoTemplate.find(query, OssConfigDO.class);
    }

    @Override
    public OssConfigDO findByUserIdAndEndpointAndBucketAndPlatform(String userId, String endpoint, String bucket, PlatformOSS platform) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("endpoint").is(endpoint));
        query.addCriteria(Criteria.where("bucket").is(bucket));
        query.addCriteria(Criteria.where("platform").is(platform));
        return mongoTemplate.findOne(query, OssConfigDO.class);
    }

    @Override
    public void updateOssConfigBy(OssConfigDO ossConfigDO) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(ossConfigDO.getUserId()));
        query.addCriteria(Criteria.where("endpoint").is(ossConfigDO.getEndpoint()));
        query.addCriteria(Criteria.where("bucket").is(ossConfigDO.getBucket()));
        query.addCriteria(Criteria.where("platform").is(ossConfigDO.getPlatform()));
        Update update = new Update();
        update.set("platform", ossConfigDO.getPlatform());
        update.set("folderName", ossConfigDO.getFolderName());
        update.set("accessKey", ossConfigDO.getAccessKey());
        update.set("secretKey", ossConfigDO.getSecretKey());
        update.set("endpoint", ossConfigDO.getEndpoint());
        update.set("region", ossConfigDO.getRegion());
        update.set("bucket", ossConfigDO.getBucket());
        update.set("userId", ossConfigDO.getUserId());
        mongoTemplate.upsert(query, update, OssConfigDO.class);
    }

    @Override
    public OssConfigDO findAndRemoveById(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("id").is(id));
        return mongoTemplate.findAndRemove(query, OssConfigDO.class);
    }
}

package com.jmal.clouddisk.dao.impl.mongodb;

import com.jmal.clouddisk.dao.IOssConfigDAO;
import com.jmal.clouddisk.oss.web.model.OssConfigDO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
    public OssConfigDO findById(String id) {
        Query query = new Query();
        return mongoTemplate.findById(query, OssConfigDO.class);
    }

    @Override
    public void updateOssConfigBy(OssConfigDO ossConfigDO) {
        mongoTemplate.save(ossConfigDO);
    }

    @Override
    public OssConfigDO findAndRemoveById(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        return mongoTemplate.findAndRemove(query, OssConfigDO.class);
    }
}

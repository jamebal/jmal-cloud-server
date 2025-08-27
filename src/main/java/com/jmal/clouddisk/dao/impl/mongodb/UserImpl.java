package com.jmal.clouddisk.dao.impl.mongodb;

import com.jmal.clouddisk.dao.IUserDAO;
import com.jmal.clouddisk.dao.mapping.UserField;
import com.jmal.clouddisk.dao.util.MongoQueryUtil;
import com.jmal.clouddisk.dao.util.MyQuery;
import com.jmal.clouddisk.dao.util.MyUpdate;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.IUserService;
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
public class UserImpl implements IUserDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public ConsumerDO save(ConsumerDO consumerDO) {
        return mongoTemplate.save(consumerDO);
    }

    @Override
    public List<ConsumerDO> findAllById(List<String> idList) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(idList));
        return mongoTemplate.find(query, ConsumerDO.class);
    }

    @Override
    public void deleteAllById(List<String> idList) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(idList));
        mongoTemplate.remove(query, ConsumerDO.class);
    }

    @Override
    public ConsumerDO findById(String userId) {
        return mongoTemplate.findById(userId, ConsumerDO.class);
    }

    @Override
    public void upsert(MyQuery myQuery, MyUpdate myUpdate) {
        Query query = MongoQueryUtil.toMongoQuery(myQuery, UserField.allFields());
        Update update = MongoQueryUtil.toMongoUpdate(myUpdate, UserField.allFields());
        mongoTemplate.upsert(query, update, ConsumerDO.class);
    }

    @Override
    public ConsumerDO findByUsername(String username) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USERNAME).is(username));
        return mongoTemplate.findOne(query, ConsumerDO.class);
    }

    @Override
    public ConsumerDO findOneByCreatorTrue() {
        Query query = new Query();
        query.addCriteria(Criteria.where("creator").is(true));
        return mongoTemplate.findOne(query, ConsumerDO.class);
    }
}

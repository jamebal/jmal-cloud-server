package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.ITagDAO;
import com.jmal.clouddisk.model.TagDO;
import com.jmal.clouddisk.service.IUserService;
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
public class TagDAOImpl implements ITagDAO {

    private final MongoTemplate mongoTemplate;

    /**
     * 获取查询条件
     * @param userId userId
     * @return Query
     */
    private Query getQueryUserId(String userId) {
        Query query = new Query();
        if (!CharSequenceUtil.isBlank(userId)) {
            query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        } else {
            query.addCriteria(Criteria.where(IUserService.USER_ID).exists(false));
        }
        return query;
    }

    @Override
    public List<TagDO> findTags(String userId) {
        Query query = getQueryUserId(userId);
        return mongoTemplate.find(query, TagDO.class);
    }

    @Override
    public TagDO findOneTagByUserIdAndName(String userId, String tagName) {
        Query query = getQueryUserId(userId);
        query.addCriteria(Criteria.where("name").is(tagName));
        return mongoTemplate.findOne(query, TagDO.class);
    }

    @Override
    public TagDO findOneTagByUserIdAndSlugName(String userId, String tagSlugName) {
        Query query = getQueryUserId(userId);
        query.addCriteria(Criteria.where("slug").is(tagSlugName));
        return mongoTemplate.findOne(query, TagDO.class);
    }
}

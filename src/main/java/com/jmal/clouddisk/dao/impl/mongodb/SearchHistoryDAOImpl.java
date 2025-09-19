package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.ISearchHistoryDAO;
import com.jmal.clouddisk.model.query.SearchOptionHistoryDO;
import com.jmal.clouddisk.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class SearchHistoryDAOImpl implements ISearchHistoryDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public void removeByUserIdAndKeyword(String keyword, String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("keyword").is(keyword));
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        mongoTemplate.remove(query, SearchOptionHistoryDO.class);
    }

    @Override
    public void removeByUserIdAndSearchTimeLessThan(String userId, long time) {
        Query query = new Query();
        query.addCriteria(Criteria.where("userId").is(userId));
        query.addCriteria(Criteria.where("searchTime").lt(time));
        mongoTemplate.remove(query, SearchOptionHistoryDO.class);
    }

    @Override
    public void save(SearchOptionHistoryDO searchOptionHistoryDO) {
        mongoTemplate.save(searchOptionHistoryDO);
    }

    @Override
    public List<SearchOptionHistoryDO> findRecentByUserIdAndKeyword(String userId, String keyword, Pageable pageable) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        if (CharSequenceUtil.isNotBlank(keyword)) {
            query.addCriteria(Criteria.where("keyword").regex(keyword));
        }
        query.with(pageable);
        return mongoTemplate.find(query, SearchOptionHistoryDO.class);
    }

    @Override
    public void removeById(String id) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, SearchOptionHistoryDO.class);
    }

    @Override
    public void removeAllByUserId(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        mongoTemplate.remove(query, SearchOptionHistoryDO.class);
    }
}

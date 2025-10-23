package com.jmal.clouddisk.dao.impl.mongodb;

import com.jmal.clouddisk.dao.IHeartwingsDAO;
import com.jmal.clouddisk.model.HeartwingsDO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class HeartwingsDAOImpl implements IHeartwingsDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public void save(HeartwingsDO heartwingsDO) {
        mongoTemplate.save(heartwingsDO);
    }

    @Override
    public ResponseResult<List<HeartwingsDO>> getWebsiteHeartwings(Integer page, Integer pageSize, String order) {
        Query query = new Query();
        long count = mongoTemplate.count(new Query(), HeartwingsDO.class);
        query.skip((long) pageSize * (page - 1));
        query.limit(pageSize);
        Sort.Direction direction = Sort.Direction.ASC;
        if (Constants.DESCENDING.equals(order)) {
            direction = Sort.Direction.DESC;
        }
        query.with(Sort.by(direction, Constants.CREATE_TIME));
        return ResultUtil.success(mongoTemplate.find(query, HeartwingsDO.class)).setCount(count);
    }
}

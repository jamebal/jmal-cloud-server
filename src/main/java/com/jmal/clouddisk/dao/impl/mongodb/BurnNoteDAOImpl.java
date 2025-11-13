package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.date.DateUnit;
import com.jmal.clouddisk.dao.BurnNoteFileService;
import com.jmal.clouddisk.dao.IBurnNoteDAO;
import com.jmal.clouddisk.model.BurnNoteDO;
import com.mongodb.client.result.DeleteResult;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class BurnNoteDAOImpl implements IBurnNoteDAO {

    private final MongoTemplate mongoTemplate;
    private final BurnNoteFileService burnNoteFileService;

    @Override
    public BurnNoteDO save(BurnNoteDO burnNoteDO) {
        Instant now = Instant.now();
        burnNoteDO.setCreatedTime(now);
        burnNoteDO.setUpdatedTime(now);
        return mongoTemplate.save(burnNoteDO);
    }

    @Override
    public BurnNoteDO findById(String id) {
        return mongoTemplate.findById(id, BurnNoteDO.class);
    }

    @Override
    public void deleteById(String id) {
        Query query = Query.query(Criteria.where("_id").is(id));
        mongoTemplate.remove(query, BurnNoteDO.class);
    }

    private List<String> findExpiredNotes() {
        Instant now = Instant.now();
        Instant expireAt = now.minusMillis(DateUnit.DAY.getMillis());

        Criteria or1 = Criteria.where("expireAt").lte(Instant.now());
        Criteria or2 = Criteria.where("createdTime").lt(expireAt);

        Query query = new Query();
        query.addCriteria(new Criteria().orOperator(or1, or2));
        query.fields().include("_id");
        return mongoTemplate.find(query, BurnNoteDO.class).stream()
                .map(BurnNoteDO::getId).toList();
    }

    @Override
    public long deleteExpiredNotes() {
        List<String> expiredNoteIds = findExpiredNotes();
        if (expiredNoteIds.isEmpty()) {
            return 0;
        }
        expiredNoteIds.forEach(burnNoteFileService::deleteAllChunks);
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").in(expiredNoteIds));
        DeleteResult deleteResult = mongoTemplate.remove(query, BurnNoteDO.class);
        return deleteResult.getDeletedCount();
    }

    @Override
    public boolean existData() {
        Query query = new Query();
        query.limit(1);
        return mongoTemplate.exists(query, BurnNoteDO.class);
    }
}

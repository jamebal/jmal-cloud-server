package com.jmal.clouddisk.dao.impl.mongodb;

import com.jmal.clouddisk.dao.IDirectLinkDAO;
import com.jmal.clouddisk.model.DirectLink;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import static com.jmal.clouddisk.service.impl.DirectLinkService.MARK;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class DirectLinkDAOImpl implements IDirectLinkDAO {

    private final MongoTemplate mongoTemplate;


    @Override
    public void removeByUserId(String userId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(IUserService.USER_ID).is(userId));
        mongoTemplate.remove(query, DirectLink.class);
    }

    @Override
    public DirectLink findByMark(String mark) {
        Query query = new Query();
        query.addCriteria(Criteria.where(MARK).is(mark));
        return mongoTemplate.findOne(query, DirectLink.class);
    }

    @Override
    public void updateByFileId(String fileId, String mark, String userId, LocalDateTime now) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.FILE_ID).is(fileId));
        Update update = new Update();
        update.set(MARK, mark);
        update.set(Constants.FILE_ID, fileId);
        update.set(IUserService.USER_ID, userId);
        update.set(Constants.UPDATE_DATE, LocalDateTime.now());
        mongoTemplate.upsert(query, update, DirectLink.class);
    }

    @Override
    public boolean existsByMark(String mark) {
        Query query = new Query();
        query.addCriteria(Criteria.where(MARK).is(mark));
        return mongoTemplate.exists(query, DirectLink.class);
    }

    @Override
    public DirectLink findByFileId(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.FILE_ID).is(fileId));
        return mongoTemplate.findOne(query, DirectLink.class);
    }
}

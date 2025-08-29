package com.jmal.clouddisk.dao.impl.mongodb;

import com.jmal.clouddisk.dao.IFileDAO;
import com.jmal.clouddisk.model.file.FileDocument;
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
public class FileDAOImpl implements IFileDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public void deleteAllByIdInBatch(List<String> userIdList) {
        Query query = new Query();
        query.addCriteria(org.springframework.data.mongodb.core.query.Criteria.where(IUserService.USER_ID).in(userIdList));
        mongoTemplate.remove(query, FileDocument.class);
    }

    @Override
    public void updateIsPublicById(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update();
        update.set("isPublic", true);
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }
}

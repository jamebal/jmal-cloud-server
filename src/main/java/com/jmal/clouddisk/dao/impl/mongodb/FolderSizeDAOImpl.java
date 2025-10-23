package com.jmal.clouddisk.dao.impl.mongodb;

import com.jmal.clouddisk.dao.IFolderSizeDAO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.service.Constants;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class FolderSizeDAOImpl implements IFolderSizeDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public List<FileDocument> findFoldersNeedUpdateSize(int batchSize) {
        Query query = Query.query(Criteria.where(Constants.IS_FOLDER).is(true).and(Constants.SIZE).exists(false)).limit(batchSize);
        return mongoTemplate.find(query, FileDocument.class);
    }

    @Override
    public void updateFileSize(String fileId, long size, int childrenCount) {
        Update update = new Update();
        update.set(Constants.SIZE, size);
        update.set(Constants.CHILDREN_COUNT, childrenCount);
        update.set(Constants.UPDATE_DATE, LocalDateTime.now());
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(fileId)), update, FileDocument.class);
    }

    @Override
    public boolean hasNeedUpdateSizeInDb() {
        Query query = Query.query(Criteria.where(Constants.IS_FOLDER).is(true).and(Constants.SIZE).exists(false));
        query.limit(1); // 只需要知道是否存在，不需要完整计数
        return mongoTemplate.exists(query, FileDocument.class);
    }

    @Override
    public long totalSizeNeedUpdateSizeInDb() {
        Query query = Query.query(Criteria.where(Constants.IS_FOLDER).is(true).and(Constants.SIZE).exists(false));
        return mongoTemplate.count(query, FileDocument.class);
    }

    @Override
    public void clearFolderSizInDb() {
        Query query = Query.query(Criteria.where(Constants.IS_FOLDER).is(true));
        Update update = new Update();
        update.unset(Constants.SIZE);
        mongoTemplate.updateMulti(query, update, FileDocument.class);
    }
}

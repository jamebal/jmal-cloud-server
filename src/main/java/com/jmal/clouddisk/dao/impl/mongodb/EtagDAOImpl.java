package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.ReUtil;
import com.jmal.clouddisk.dao.IEtagDAO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.dto.FileBaseEtagDTO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.impl.CommonFileService;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.result.UpdateResult;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import static com.jmal.clouddisk.service.IUserService.USER_ID;
import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Aggregates.match;
import static com.mongodb.client.model.Filters.*;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class EtagDAOImpl implements IEtagDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public long countFoldersWithoutEtag() {
        Query queryNoEtagQuery = getNoEtagQuery();
        return mongoTemplate.count(queryNoEtagQuery, FileDocument.class);
    }

    private static Query getNoEtagQuery() {
        Query queryNoEtagQuery = new Query();
        queryNoEtagQuery.addCriteria(Criteria.where(Constants.ETAG).exists(false).and(Constants.IS_FOLDER).is(true));
        return queryNoEtagQuery;
    }

    @Override
    public void setFoldersWithoutEtag() {
        Query queryNoEtagQuery = getNoEtagQuery();
        Update update = new Update().set(Constants.NEEDS_ETAG_UPDATE_FIELD, true).currentDate(Constants.LAST_ETAG_UPDATE_REQUEST_AT_FIELD);
        mongoTemplate.updateMulti(queryNoEtagQuery, update, FileDocument.class);
    }

    @Override
    public long getFolderSize(String userId, String path) {
        List<Bson> list = Arrays.asList(match(and(eq(USER_ID, userId), eq(Constants.IS_FOLDER, false), regex("path", "^" + ReUtil.escape(path)))), group(null, sum(Constants.TOTAL_SIZE, "$size")));
        AggregateIterable<Document> result = mongoTemplate.getCollection(CommonFileService.COLLECTION_NAME).aggregate(list);
        long totalSize = 0;
        Document doc = result.first();
        if (doc != null) {
            totalSize = Convert.toLong(doc.get(Constants.TOTAL_SIZE), 0L);
        }
        return totalSize;
    }

    @Override
    public boolean existsByNeedsEtagUpdateFolder() {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.NEEDS_ETAG_UPDATE_FIELD).is(true).and(Constants.IS_FOLDER).is(true));
        return mongoTemplate.exists(query, FileDocument.class);
    }

    @Override
    public String findEtagByUserIdAndPathAndName(String userId, String path, String name) {
        Query query = FileDAOImpl.getQuery(userId, path, name);
        FileDocument fileDocument = mongoTemplate.findOne(query, FileDocument.class);
        return fileDocument != null ? fileDocument.getEtag() : null;
    }

    @Override
    public void setEtagByUserIdAndPathAndName(String userId, String path, String name, String newEtag) {
        Query query = FileDAOImpl.getQuery(userId, path, name);
        Update update = new Update().set(Constants.ETAG, newEtag).set(Constants.NEEDS_ETAG_UPDATE_FIELD, false);
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    @Override
    public boolean existsByUserIdAndPath(String userId, String path) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USER_ID).is(userId).and(Constants.PATH_FIELD).is(path));
        return mongoTemplate.exists(query, FileDocument.class);
    }

    @Override
    public long countRootDirFilesWithoutEtag() {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.PATH_FIELD).is("/").and(Constants.ETAG).exists(false).and(Constants.IS_FOLDER).is(false));
        return mongoTemplate.count(query, FileDocument.class);
    }

    @Override
    public List<FileBaseEtagDTO> findFileBaseEtagDTOByRootDirFilesWithoutEtag() {
        Query query = new Query();
        query.addCriteria(Criteria.where(Constants.PATH_FIELD).is("/").and(Constants.ETAG).exists(false).and(Constants.IS_FOLDER).is(false));
        query.limit(16);
        return mongoTemplate.find(query, FileBaseEtagDTO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public List<FileBaseEtagDTO> findFileBaseEtagDTOByNeedUpdateFolder(Sort sort) {
        Query query = new Query();
        query.addCriteria(Criteria
                .where(Constants.NEEDS_ETAG_UPDATE_FIELD).is(true)
                .and(Constants.IS_FOLDER).is(true));
        query.limit(16);
        query.with(sort);
        return mongoTemplate.find(query, FileBaseEtagDTO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public void clearMarkUpdateById(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update().set(Constants.NEEDS_ETAG_UPDATE_FIELD, false);
        update.unset(Constants.ETAG_UPDATE_FAILED_ATTEMPTS_FIELD);
        update.unset(Constants.LAST_ETAG_UPDATE_ERROR_FIELD);
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }

    @Override
    public boolean setMarkUpdateByUserIdAndPathAndName(String userId, String path, String name) {
        Query query = FileDAOImpl.getQuery(userId, path, name);
        Update update = new Update().set(Constants.NEEDS_ETAG_UPDATE_FIELD, true).currentDate(Constants.LAST_ETAG_UPDATE_REQUEST_AT_FIELD);
        UpdateResult updateResult = mongoTemplate.updateFirst(query, update, FileDocument.class);
        return updateResult.getModifiedCount() > 0 || updateResult.getMatchedCount() > 0;
    }

    @Override
    public List<FileBaseEtagDTO> findFileBaseEtagDTOByUserIdAndPath(String userId, String path) {
        Query query = new Query();
        query.addCriteria(Criteria.where(USER_ID).is(userId).and(Constants.PATH_FIELD).is(path));
        return mongoTemplate.find(query, FileBaseEtagDTO.class, CommonFileService.COLLECTION_NAME);
    }

    @Override
    public long updateEtagAndSizeById(String fileId, String etag, long size) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update().set(Constants.ETAG, etag).set(Constants.SIZE, size);
        UpdateResult updateResult = mongoTemplate.updateFirst(query, update, FileDocument.class);
        return updateResult.getModifiedCount();
    }

    @Override
    public int findEtagUpdateFailedAttemptsById(String fileId) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        query.fields().include(Constants.ETAG_UPDATE_FAILED_ATTEMPTS_FIELD);
        FileDocument failedDoc = mongoTemplate.findOne(query, FileDocument.class);
        return (failedDoc != null && failedDoc.getEtagUpdateFailedAttempts() != null) ? failedDoc.getEtagUpdateFailedAttempts() + 1 : 1;
    }

    @Override
    public void setFailedEtagById(String fileId, int attempts, String errorMsg, Boolean needsEtagUpdate) {
        Query query = new Query();
        query.addCriteria(Criteria.where("_id").is(fileId));
        Update update = new Update()
                .set(Constants.ETAG_UPDATE_FAILED_ATTEMPTS_FIELD, attempts)
                .set(Constants.LAST_ETAG_UPDATE_ERROR_FIELD, errorMsg);
        if (needsEtagUpdate != null) {
            update.set(Constants.NEEDS_ETAG_UPDATE_FIELD, needsEtagUpdate);
        }
        mongoTemplate.updateFirst(query, update, FileDocument.class);
    }
}

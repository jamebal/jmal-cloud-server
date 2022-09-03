package com.jmal.clouddisk.service;

import com.jmal.clouddisk.service.impl.FileServiceImpl;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.model.IndexModel;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jmal
 * @Description fileDocument 索引
 * @date 2022/9/2 17:10
 */
@Service
@Slf4j
public class MongodbIndex {

    private final MongoTemplate mongoTemplate;

    public MongodbIndex(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public void checkMongoIndex() {

        // fileDocument 创建索引
        fileDocumentIndex();
        // log 创建索引
        logIndex();

    }

    private void logIndex() {
        List<IndexModel> indexLogList = new ArrayList<>();

        indexLogList.add(new IndexModel(new Document("createTime", 1)));

        indexLogList.add(new IndexModel(new Document("time", 1)));

        indexLogList.add(new IndexModel(new Document("username", 1)));

        indexLogList.add(new IndexModel(new Document("showName", 1)));

        indexLogList.add(new IndexModel(new Document("ip", 1)));

        indexLogList.add(new IndexModel(new Document("cityIp", 1)));

        indexLogList.add(new IndexModel(new Document("url", 1)));

        indexLogList.add(new IndexModel(new Document("status", 1)));

        indexLogList.add(new IndexModel(new Document("operationModule", 1)));

        indexLogList.add(new IndexModel(new Document("operationFun", 1)));

        indexLogList.add(new IndexModel(new Document("deviceModel", 1)));

        indexLogList.add(new IndexModel(new Document("operatingSystem", 1)));

        indexLogList.add(new IndexModel(new Document("type", 1)));

        Document indexTypeCreateTime = new Document("type", 1);
        indexTypeCreateTime.put("createTime", 1);
        indexLogList.add(new IndexModel(indexTypeCreateTime));

        Document indexTypeUsernameCreateTime = new Document("type", 1);
        indexTypeUsernameCreateTime.put("createTime", 1);
        indexTypeUsernameCreateTime.put("username", 1);
        indexLogList.add(new IndexModel(indexTypeUsernameCreateTime));

        Document indexTypeUsername = new Document("type", 1);
        indexTypeUsername.put("username", 1);
        indexLogList.add(new IndexModel(indexTypeUsername));

        ListIndexesIterable<Document> listIndexesIterable = mongoTemplate.getCollection("log").listIndexes();
        long indexCount = 0;
        for (Document ignored : listIndexesIterable) {
            indexCount++;
        }

        if (indexCount < 2) {
            mongoTemplate.getCollection("log").createIndexes(indexLogList);
        }

    }

    private void fileDocumentIndex() {
        List<IndexModel> indexModelList = new ArrayList<>();

        indexModelList.add(new IndexModel(new Document("name", 1)));

        indexModelList.add(new IndexModel(new Document("size", 1)));

        indexModelList.add(new IndexModel(new Document("updateDate", 1)));

        Document indexPathName = new Document("path", 1);
        indexPathName.put("name", 1);
        indexModelList.add(new IndexModel(indexPathName));

        Document indexUserIdMdFivePath = new Document("userId", 1);
        indexUserIdMdFivePath.put("md5", 1);
        indexUserIdMdFivePath.put("path", 1);
        indexModelList.add(new IndexModel(indexUserIdMdFivePath));

        Document indexUserIdPath = new Document("userId", 1);
        indexUserIdPath.put("path", 1);
        indexModelList.add(new IndexModel(indexUserIdPath));

        Document indexUserIdIsFolderPath = new Document("userId", 1);
        indexUserIdIsFolderPath.put("path", 1);
        indexUserIdIsFolderPath.put("isFolder", 1);
        indexModelList.add(new IndexModel(indexUserIdIsFolderPath));

        Document indexUserIdIsFolderPathName = new Document("userId", 1);
        indexUserIdIsFolderPathName.put("path", 1);
        indexUserIdIsFolderPathName.put("isFolder", 1);
        indexUserIdIsFolderPathName.put("name", 1);
        indexModelList.add(new IndexModel(indexUserIdIsFolderPathName));

        Document indexUserIdIsFolder = new Document("userId", 1);
        indexUserIdIsFolder.put("isFolder", 1);
        indexModelList.add(new IndexModel(indexUserIdIsFolder));

        Document indexUserIdIsFavorite = new Document("userId", 1);
        indexUserIdIsFavorite.put("isFavorite", 1);
        indexModelList.add(new IndexModel(indexUserIdIsFavorite));

        Document indexUserIdContentType = new Document("userId", 1);
        indexUserIdContentType.put("contentType", 1);
        indexModelList.add(new IndexModel(indexUserIdContentType));

        ListIndexesIterable<Document> listIndexesIterable = mongoTemplate.getCollection(FileServiceImpl.COLLECTION_NAME).listIndexes();
        long indexCount = 0;
        for (Document ignored : listIndexesIterable) {
            indexCount++;
        }

        if (indexCount < 2) {
            mongoTemplate.getCollection(FileServiceImpl.COLLECTION_NAME).createIndexes(indexModelList);
        }

    }
}

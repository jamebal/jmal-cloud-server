package com.jmal.clouddisk.mongodb;

import cn.hutool.core.date.TimeInterval;
import cn.hutool.core.lang.Console;
import com.jmal.clouddisk.service.Constants;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author jmal
 * @Description 查找文件的父级目录
 * @date 2023/3/8 15:27
 */
@SpringBootTest
class RegexTest {
    MongoTemplate mongoTemplate;

    @Test
    void regex() {
        MongoDatabaseFactory mongoDatabaseFactory = new SimpleMongoClientDatabaseFactory("mongodb://localhost:27017/jmalcloud");
        mongoTemplate = new MongoTemplate(mongoDatabaseFactory);
        TimeInterval interval = new TimeInterval();
        Path path = Paths.get("/合并文件测试/mall-admin-web/node_modules/brorand/");

        StringBuilder pathStr = new StringBuilder("/");
        List<Document> documentList = new ArrayList<>(path.getNameCount());
        for (int i = 0; i < path.getNameCount(); i++) {
            String filename = path.getName(i).toString();
            if (i > 0) {
                pathStr.append("/");
            }
            Document document = new Document("path", pathStr.toString()).append("name", filename);
            documentList.add(document);
            pathStr.append(filename);
        }
        if (documentList.isEmpty()) {
            return;
        }
        List<Document> list = Arrays.asList(new Document("$match", new Document("$or", documentList)), new Document("$match", new Document(Constants.SHARE_BASE, true)));
        AggregateIterable<Document> result = mongoTemplate.getCollection("fileDocument").aggregate(list);
        Document document = null;
        try (MongoCursor<Document> mongoCursor = result.iterator()) {
            while (mongoCursor.hasNext()) {
                document = mongoCursor.next();
            }
        }
        Console.log("document: ", document);
        Console.log("查询耗时: {}ms", interval.interval());
        assertNotNull(document, "The value should not null");
    }
}

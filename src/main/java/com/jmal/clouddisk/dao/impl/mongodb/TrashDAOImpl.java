package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.convert.Convert;
import com.jmal.clouddisk.dao.ITrashDAO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.service.IUserService;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.model.Aggregates;
import lombok.RequiredArgsConstructor;
import org.bson.BsonNull;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

import static com.mongodb.client.model.Accumulators.sum;
import static com.mongodb.client.model.Aggregates.group;
import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class TrashDAOImpl implements ITrashDAO {

    private final MongoTemplate mongoTemplate;


    @Override
    public long getOccupiedSpace(String userId, String collectionName) {
        Long space = 0L;
        List<Bson> list = Arrays.asList(Aggregates.match(and(eq(IUserService.USER_ID, userId), eq(Constants.IS_FOLDER, false))), group(new BsonNull(), sum(Constants.TOTAL_SIZE, "$size")));
        AggregateIterable<Document> aggregateIterable = mongoTemplate.getCollection(collectionName).aggregate(list);
        Document doc = aggregateIterable.first();
        if (doc != null) {
            space = Convert.toLong(doc.get(Constants.TOTAL_SIZE), 0L);
        }
        return space;
    }

}

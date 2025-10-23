package com.jmal.clouddisk.dao.util;

import com.jmal.clouddisk.dao.mapping.FieldMapping;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.Arrays;

public class MongoQueryUtil {
    public static Query toMongoQuery(MyQuery myQuery, FieldMapping[] fields) {
        Criteria criteria = new Criteria();
        for (var entry : myQuery.getEqMap().entrySet()) {
            // 逻辑字段名转Mongo物理字段名
            FieldMapping field = Arrays.stream(fields)
                .filter(f -> f.getLogical().equals(entry.getKey()))
                .findFirst().orElse(null);
            String mongoField = field != null ? field.getMongo() : entry.getKey();
            criteria = criteria.and(mongoField).is(entry.getValue());
        }
        return new Query(criteria);
    }

    public static Update toMongoUpdate(MyUpdate myUpdate, FieldMapping[] fields) {
        Update update = new Update();

        for (var entry : myUpdate.getOperations().entrySet()) {
            String logicalField = entry.getKey();
            Object value = entry.getValue();

            FieldMapping field = Arrays.stream(fields)
                    .filter(f -> f.getLogical().equals(logicalField))
                    .findFirst().orElse(null);
            String mongoField = field != null ? field.getMongo() : logicalField;

            if (value == MyUpdate.UNSET) {
                update.unset(mongoField);
            } else {
                update.set(mongoField, value);
            }
        }

        return update;
    }
}

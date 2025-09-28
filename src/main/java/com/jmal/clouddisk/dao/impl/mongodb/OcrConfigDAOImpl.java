package com.jmal.clouddisk.dao.impl.mongodb;

import com.jmal.clouddisk.dao.IOcrConfigDAO;
import com.jmal.clouddisk.ocr.OcrConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OcrConfigDAOImpl implements IOcrConfigDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public OcrConfig findOcrConfig() {
        return mongoTemplate.findAll(OcrConfig.class).stream().findFirst().orElse(null);
    }

    @Override
    public void save(OcrConfig config) {
        mongoTemplate.save(config);
    }
}

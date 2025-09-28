package com.jmal.clouddisk.dao.impl.mongodb;

import com.jmal.clouddisk.dao.ITranscodeConfigDAO;
import com.jmal.clouddisk.media.TranscodeConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TranscodeConfigDAOImpl implements ITranscodeConfigDAO {

    private final MongoTemplate mongoTemplate;


    @Override
    public TranscodeConfig findTranscodeConfig() {
        return mongoTemplate.findAll(TranscodeConfig.class).stream().findFirst().orElse(null);
    }

    @Override
    public void save(TranscodeConfig config) {
        mongoTemplate.save(config);
    }
}

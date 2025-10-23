package com.jmal.clouddisk.dao.impl.mongodb;

import com.jmal.clouddisk.dao.IOfficeConfigDAO;
import com.jmal.clouddisk.office.model.OfficeConfigDO;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class OfficeConfigDAOImpl implements IOfficeConfigDAO {

    private final MongoTemplate mongoTemplate;


    @Override
    public synchronized void upsert(OfficeConfigDO officeConfigDO) {
        mongoTemplate.findAll(OfficeConfigDO.class).stream().findFirst().ifPresent(existingConfig -> officeConfigDO.setId(existingConfig.getId()));
        mongoTemplate.save(officeConfigDO);
    }

    @Override
    public OfficeConfigDO findOne() {
        return mongoTemplate.findAll(OfficeConfigDO.class).stream().findFirst().orElse(null);
    }
}

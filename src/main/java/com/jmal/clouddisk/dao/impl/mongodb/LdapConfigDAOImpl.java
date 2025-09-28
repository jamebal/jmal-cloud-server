package com.jmal.clouddisk.dao.impl.mongodb;

import com.jmal.clouddisk.dao.ILdapConfigDAO;
import com.jmal.clouddisk.model.LdapConfigDO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LdapConfigDAOImpl implements ILdapConfigDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public LdapConfigDO findOne() {
        return mongoTemplate.findOne(new Query(), LdapConfigDO.class);
    }

    @Override
    public void save(LdapConfigDO ldapConfigDO) {
        mongoTemplate.save(ldapConfigDO);
    }
}

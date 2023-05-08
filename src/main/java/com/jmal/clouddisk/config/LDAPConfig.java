package com.jmal.clouddisk.config;

import com.jmal.clouddisk.model.LdapDO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.support.LdapContextSource;

import java.util.Hashtable;

@Configuration
public class LDAPConfig {

    @Autowired
    MongoTemplate mongoTemplate;

    @Bean
    LdapContextSource contextSource() {
        LdapDO ldapDO = mongoTemplate.findOne(new Query(), LdapDO.class);
        if (ldapDO != null) {
            LdapContextSource contextSource = new LdapContextSource();
            contextSource.setUrl("ldap://" + ldapDO.getLdapHost() + ":" + ldapDO.getPort());
            contextSource.setBase(ldapDO.getBaseDN());
            contextSource.setPooled(true);
            contextSource.setBaseEnvironmentProperties(new Hashtable<>());
            return contextSource;
        }
        return new LdapContextSource();
    }

    @Bean
    public LdapTemplate ldapTemplate(LdapContextSource contextSource) {
        return new LdapTemplate(contextSource);
    }

}

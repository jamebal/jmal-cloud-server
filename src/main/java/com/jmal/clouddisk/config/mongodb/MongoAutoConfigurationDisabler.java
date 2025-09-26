package com.jmal.clouddisk.config.mongodb;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.data.ldap.LdapRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoReactiveRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnExpression(
        "'${jmalcloud.datasource.type}'!='mongodb' && '${jmalcloud.datasource.migration}'!='true'"
)
@EnableAutoConfiguration(exclude = {
        // MongoDB相关
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class,
        MongoRepositoriesAutoConfiguration.class,
        MongoReactiveAutoConfiguration.class,
        MongoReactiveDataAutoConfiguration.class,
        MongoReactiveRepositoriesAutoConfiguration.class,
        // LDAP相关
        LdapAutoConfiguration.class,
        LdapRepositoriesAutoConfiguration.class,
})
public class MongoAutoConfigurationDisabler {
    // 这个类专门用于在选择 JPA 时禁用 MongoDB 的自动配置
}

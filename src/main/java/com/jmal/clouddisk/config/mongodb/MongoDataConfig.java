package com.jmal.clouddisk.config.mongodb;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "jmalcloud.datasource.mongo-enabled")
// @EnableMongoRepositories(basePackages = "com.jmal.clouddisk.dao.repository.mongo")
public class MongoDataConfig {
}

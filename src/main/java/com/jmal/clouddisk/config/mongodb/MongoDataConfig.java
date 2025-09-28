package com.jmal.clouddisk.config.mongodb;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@Conditional(MongoDataSourceCondition.class)
@EnableMongoRepositories(basePackages = "com.jmal.clouddisk.dao.repository.mongo")
public class MongoDataConfig {}

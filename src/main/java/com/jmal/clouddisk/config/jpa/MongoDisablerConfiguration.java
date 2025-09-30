// package com.jmal.clouddisk.config.jpa;
//
// import com.mongodb.client.MongoClient;
// import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
//
// @Configuration
// @ConditionalOnProperty(name = "jmalcloud.datasource.mongo-enabled", havingValue = "false", matchIfMissing = true)
// public class MongoDisablerConfiguration {
//
//     @Bean
//     public MongoClient mongoClient() {
//         return new NoOpMongoClient();
//     }
// }

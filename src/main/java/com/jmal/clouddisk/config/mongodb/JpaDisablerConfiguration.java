// package com.jmal.clouddisk.config.mongodb;
//
// import jakarta.persistence.EntityManagerFactory;
// import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
// import org.springframework.context.annotation.Bean;
// import org.springframework.context.annotation.Configuration;
//
// import javax.sql.DataSource;
//
// /**
//  * 当使用 MongoDB 作为数据源时，提供一个占位的 EntityManagerFactory Bean。
//  */
// @Configuration
// @ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
// public class JpaDisablerConfiguration {
//
//     @Bean
//     public DataSource dataSource() {
//         return new NoOpDataSource();
//     }
//
//     @Bean
//     public EntityManagerFactory entityManagerFactory() {
//         // 返回一个假的、空的实现
//         return new NoOpEntityManagerFactory();
//     }
// }

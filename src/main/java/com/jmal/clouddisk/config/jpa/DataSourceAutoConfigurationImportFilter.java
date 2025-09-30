// package com.jmal.clouddisk.config.jpa;
//
// import lombok.extern.slf4j.Slf4j;
// import org.jetbrains.annotations.NotNull;
// import org.springframework.boot.autoconfigure.AutoConfigurationImportFilter;
// import org.springframework.boot.autoconfigure.AutoConfigurationMetadata;
// import org.springframework.context.EnvironmentAware;
// import org.springframework.core.env.Environment;
//
// import java.util.Arrays;
// import java.util.HashSet;
// import java.util.Set;
//
// /**
//  * 一个AOT兼容的自动配置导入过滤器。
//  * 它通过实现 EnvironmentAware 接口，在启动的早期阶段获取到 Environment 对象，
//  * 然后根据运行时的 "特性开关" 属性，动态地从自动配置列表中排除掉
//  * 不需要的数据源（JPA 或 Mongo）相关的自动配置。
//  * 这是在Native Image中实现动态数据源切换的终极、最可靠的方案。
//  */
// @Slf4j
// public class DataSourceAutoConfigurationImportFilter implements AutoConfigurationImportFilter, EnvironmentAware {
//
//     // 在这里列出，当处于非JPA模式时，我们希望彻底禁用的所有自动配置类
//     private static final Set<String> JPA_AUTO_CONFIGURATIONS = new HashSet<>(Arrays.asList(
//             "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
//             "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
//             "org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration",
//             "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
//             "org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration",
//             "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
//             "org.springframework.boot.autoconfigure.transaction.jta.JtaAutoConfiguration"
//     ));
//
//     // 同样地，定义非Mongo模式下要禁用的
//     private static final Set<String> MONGO_AUTO_CONFIGURATIONS = new HashSet<>(Arrays.asList(
//             "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
//             "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
//             "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
//             "org.springframework.boot.autoconfigure.mongo.MongoReactiveAutoConfiguration",
//             "org.springframework.boot.autoconfigure.data.mongo.MongoReactiveDataAutoConfiguration",
//             "org.springframework.boot.autoconfigure.data.mongo.MongoReactiveRepositoriesAutoConfiguration"
//     ));
//
//     private Environment environment;
//
//     /**
//      * 这是 EnvironmentAware 接口的方法。
//      * Spring容器在创建这个Filter的实例后，会自动调用这个方法，
//      * 将当前的 Environment 对象传递进来。
//      *
//      * @param environment 当前的应用环境
//      */
//     @Override
//     public void setEnvironment(@NotNull Environment environment) {
//         this.environment = environment;
//     }
//
//     @Override
//     public boolean[] match(String[] autoConfigurationClasses, AutoConfigurationMetadata autoConfigurationMetadata) {
//         if (this.environment == null) {
//             // 这是一个防御性检查，理论上不应该发生
//             // 如果由于某种原因 environment 未被注入，我们默认全部通过，以避免意外地禁用所有配置
//             boolean[] matches = new boolean[autoConfigurationClasses.length];
//             Arrays.fill(matches, true);
//             return matches;
//         }
//
//         // 从已经注入的 environment 成员变量中获取我们的“特性开关”
//         boolean jpaEnabled = this.environment.getProperty("jmalcloud.datasource.jpa-enabled", Boolean.class, false);
//         boolean mongoEnabled = this.environment.getProperty("jmalcloud.datasource.mongo-enabled", Boolean.class, false);
//
//         boolean[] matches = new boolean[autoConfigurationClasses.length];
//         for (int i = 0; i < autoConfigurationClasses.length; i++) {
//             String className = autoConfigurationClasses[i];
//
//             matches[i] = true; // 默认全部匹配
//
//             // 如果JPA被禁用了，并且当前类在我们的JPA禁用列表里，那么就不匹配它 (返回false)
//             if (!jpaEnabled && JPA_AUTO_CONFIGURATIONS.contains(className)) {
//                 log.info("Disabling JPA auto-configuration: {}", className);
//                 matches[i] = false;
//             }
//
//             // 如果Mongo被禁用了，并且当前类在我们的Mongo禁用列表里，那么就不匹配它
//             if (!mongoEnabled && MONGO_AUTO_CONFIGURATIONS.contains(className)) {
//                 log.info("Disabling Mongo auto-configuration: {}", className);
//                 matches[i] = false;
//             }
//         }
//
//         return matches;
//     }
// }

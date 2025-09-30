package com.jmal.clouddisk.config.mongodb;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
@ConditionalOnProperty(name = "jmalcloud.datasource.jpa-enabled", havingValue = "false", matchIfMissing = true)
public class JpaDisablerConfiguration {

    @Bean
    public DataSource dataSource() {
        return new NoOpDataSource();
    }

    @Bean
    public EntityManagerFactory entityManagerFactory() {
        return new NoOpEntityManagerFactory();
    }
}

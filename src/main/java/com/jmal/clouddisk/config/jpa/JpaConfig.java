package com.jmal.clouddisk.config.jpa;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@ConditionalOnProperty(name = "jmalcloud.datasource.jpa-enabled")
@Configuration
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.jmal.clouddisk.dao.repository.jpa")
public class JpaConfig {
}

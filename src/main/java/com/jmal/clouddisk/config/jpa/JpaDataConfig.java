package com.jmal.clouddisk.config.jpa;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@Conditional(RelationalDataSourceCondition.class)
@EnableJpaRepositories(basePackages = "com.jmal.clouddisk.dao.repository.jpa")
public class JpaDataConfig {}

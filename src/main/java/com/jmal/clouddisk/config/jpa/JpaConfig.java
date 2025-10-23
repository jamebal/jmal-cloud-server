package com.jmal.clouddisk.config.jpa;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Conditional(RelationalDataSourceCondition.class)
@Configuration
@EnableJpaAuditing
public class JpaConfig {
}

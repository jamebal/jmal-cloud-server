package com.jmal.clouddisk.dao.impl.jpa.write;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.config.jpa.RelationalNotSqliteDataSourceCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
@Conditional(RelationalDataSourceCondition.class)
public class WriteServiceConfiguration {

    @Bean("directWriteService")
    @Conditional(RelationalNotSqliteDataSourceCondition.class)
    public IWriteService directWriteService(DataManipulationService dataManipulationService) {
        return new DirectWriteServiceImpl(dataManipulationService);
    }

    @Bean("queuedWriteService")
    @ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "sqlite")
    public IWriteService queuedWriteService(DataManipulationService dataManipulationService) {
        return new QueuedWriteServiceImpl(dataManipulationService);
    }

    @Bean
    @Primary
    public IWriteService safeWriteService(IWriteService actualWriteService) {
        return new SafeWriteServiceDecorator(actualWriteService);
    }
}

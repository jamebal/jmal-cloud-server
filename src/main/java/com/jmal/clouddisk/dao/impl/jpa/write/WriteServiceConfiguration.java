package com.jmal.clouddisk.dao.impl.jpa.write;

import com.jmal.clouddisk.dao.DataSourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnProperty(name = "jmalcloud.datasource.jpa-enabled")
@RequiredArgsConstructor
public class WriteServiceConfiguration {

    private final Environment environment;

    @Bean
    public IWriteService actualWriteService(DataManipulationService dataManipulationService) {
        if (isSqlite()) {
            return new QueuedWriteServiceImpl(dataManipulationService);
        } else {
            return new DirectWriteServiceImpl(dataManipulationService);
        }
    }

    @Bean
    @Primary
    public IWriteService safeWriteService(IWriteService actualWriteService) {
        return new SafeWriteServiceDecorator(actualWriteService);
    }

    private boolean isSqlite() {
        String dataSourceTypeStr = environment.getProperty("jmalcloud.datasource.type");
        DataSourceType dataSourceType = DataSourceType.fromCode(dataSourceTypeStr);
        return dataSourceType == DataSourceType.sqlite;
    }
}

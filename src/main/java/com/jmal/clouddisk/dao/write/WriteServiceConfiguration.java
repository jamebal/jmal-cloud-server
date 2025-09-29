package com.jmal.clouddisk.dao.write;

import com.jmal.clouddisk.dao.DataSourceType;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
@ConditionalOnProperty(name = "jmalcloud.datasource.jpa-enabled")
@RequiredArgsConstructor
public class WriteServiceConfiguration {

    private final Environment environment;

    @Bean
    public IWriteService directWriteService(DataManipulationService dataManipulationService) {
        IWriteService actualWriteService;
        if (isSqlite()) {
            actualWriteService = new QueuedWriteServiceImpl(dataManipulationService);
        } else {
            actualWriteService = new DirectWriteServiceImpl(dataManipulationService);
        }
        return new SafeWriteServiceDecorator(actualWriteService);
    }

    private boolean isSqlite() {
        String dataSourceTypeStr = environment.getProperty("jmalcloud.datasource.type");
        DataSourceType dataSourceType = DataSourceType.fromCode(dataSourceTypeStr);
        return dataSourceType == DataSourceType.sqlite;
    }
}

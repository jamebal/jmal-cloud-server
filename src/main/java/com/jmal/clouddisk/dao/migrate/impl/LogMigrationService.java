package com.jmal.clouddisk.dao.migrate.impl;

import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.IWriteCommon;
import com.jmal.clouddisk.dao.impl.jpa.repository.LogRepository;
import com.jmal.clouddisk.dao.migrate.IMigrationService;
import com.jmal.clouddisk.dao.migrate.MigrationResult;
import com.jmal.clouddisk.dao.migrate.MigrationUtils;
import com.jmal.clouddisk.model.LogOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.migration")
@Conditional(RelationalDataSourceCondition.class)
public class LogMigrationService implements IMigrationService {

    private final MongoTemplate mongoTemplate;

    private final LogRepository logOperation;

    private final DataSourceProperties dataSourceProperties;

    private final IWriteCommon<LogOperation> writeCommon;

    @Override
    public String getName() {
        return "日志";
    }

    @Override
    public MigrationResult migrateData() {
        return MigrationUtils.migrateMongoToJpa(
                dataSourceProperties.getType(),
                getName(),
                mongoTemplate,
                logOperation,
                writeCommon,
                LogOperation.class,
                1000 // Batch size
        );
    }

}

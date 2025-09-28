package com.jmal.clouddisk.dao.migrate.impl;

import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IOfficeConfigDAO;
import com.jmal.clouddisk.dao.migrate.IMigrationService;
import com.jmal.clouddisk.dao.migrate.MigrationResult;
import com.jmal.clouddisk.dao.migrate.MigrationUtils;
import com.jmal.clouddisk.dao.repository.jpa.OfficeConfigRepository;
import com.jmal.clouddisk.office.model.OfficeConfigDO;
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
public class OfficeConfigMigrationService implements IMigrationService {

    private final MongoTemplate mongoTemplate;

    private final OfficeConfigRepository officeConfigRepository;

    private final DataSourceProperties dataSourceProperties;

    private final IOfficeConfigDAO officeConfigDAO;

    @Override
    public String getName() {
        return "OnlyOffice配置";
    }

    @Override
    public MigrationResult migrateData() {
        return MigrationUtils.migrateMongoToJpa(
                dataSourceProperties.getType(),
                getName(),
                mongoTemplate,
                officeConfigRepository,
                officeConfigDAO,
                OfficeConfigDO.class,
                1
        );
    }

}

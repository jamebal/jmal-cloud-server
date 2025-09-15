package com.jmal.clouddisk.dao.migrate.impl;

import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.IWriteCommon;
import com.jmal.clouddisk.dao.impl.jpa.repository.OssConfigRepository;
import com.jmal.clouddisk.dao.migrate.IMigrationService;
import com.jmal.clouddisk.dao.migrate.MigrationResult;
import com.jmal.clouddisk.dao.migrate.MigrationUtils;
import com.jmal.clouddisk.oss.web.model.OssConfigDO;
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
public class OssConfigMigrationService implements IMigrationService {

    private final MongoTemplate mongoTemplate;

    private final OssConfigRepository ossConfigRepository;

    private final DataSourceProperties dataSourceProperties;

    private final IWriteCommon<OssConfigDO> writeCommon;

    @Override
    public String getName() {
        return "OSS配置";
    }

    @Override
    public MigrationResult migrateData() {
        return MigrationUtils.migrateMongoToJpa(
                dataSourceProperties.getType(),
                getName(),
                mongoTemplate,
                ossConfigRepository,
                writeCommon,
                OssConfigDO.class,
                1000 // Batch size
        );
    }

}

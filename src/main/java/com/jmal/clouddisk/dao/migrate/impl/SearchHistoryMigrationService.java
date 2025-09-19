package com.jmal.clouddisk.dao.migrate.impl;

import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.IWriteCommon;
import com.jmal.clouddisk.dao.impl.jpa.repository.SearchHistoryRepository;
import com.jmal.clouddisk.dao.migrate.IMigrationService;
import com.jmal.clouddisk.dao.migrate.MigrationResult;
import com.jmal.clouddisk.dao.migrate.MigrationUtils;
import com.jmal.clouddisk.model.query.SearchOptionHistoryDO;
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
public class SearchHistoryMigrationService implements IMigrationService {

    private final MongoTemplate mongoTemplate;

    private final SearchHistoryRepository searchHistoryRepository;

    private final DataSourceProperties dataSourceProperties;

    private final IWriteCommon<SearchOptionHistoryDO> writeCommon;

    @Override
    public String getName() {
        return "搜索历史";
    }

    @Override
    public MigrationResult migrateData() {
        return MigrationUtils.migrateMongoToJpa(
                dataSourceProperties.getType(),
                getName(),
                mongoTemplate,
                searchHistoryRepository,
                writeCommon,
                SearchOptionHistoryDO.class,
                1
        );
    }

}

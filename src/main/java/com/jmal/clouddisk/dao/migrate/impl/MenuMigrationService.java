package com.jmal.clouddisk.dao.migrate.impl;

import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IMenuDAO;
import com.jmal.clouddisk.dao.impl.jpa.IWriteCommon;
import com.jmal.clouddisk.dao.repository.jpa.MenuRepository;
import com.jmal.clouddisk.dao.migrate.IMigrationService;
import com.jmal.clouddisk.dao.migrate.MigrationResult;
import com.jmal.clouddisk.dao.migrate.MigrationUtils;
import com.jmal.clouddisk.model.rbac.MenuDO;
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
public class MenuMigrationService implements IMigrationService {

    private final MongoTemplate mongoTemplate;

    private final MenuRepository menuRepository;

    private final DataSourceProperties dataSourceProperties;

    private final IMenuDAO menuDAO;

    @Override
    public String getName() {
        return "菜单";
    }

    @Override
    public MigrationResult migrateData() {
        IWriteCommon<MenuDO> writeCommon = (IWriteCommon<MenuDO>) menuDAO;
        return MigrationUtils.migrateMongoToJpa(
                dataSourceProperties.getType(),
                getName(),
                mongoTemplate,
                menuRepository,
                writeCommon,
                MenuDO.class,
                1000 // Batch size
        );
    }

}

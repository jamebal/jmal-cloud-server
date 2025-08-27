package com.jmal.clouddisk.dao.impl.jpa.migration;

import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.migration")
@Conditional(RelationalDataSourceCondition.class)
public class MigrationRunner {

    private final UserMigrationService userMigrationService;
    private final WebsiteSettingMigrationService websiteSettingMigrationService;
    private final AccessTokenMigrationService accessTokenMigrationService;

    // 可以在应用启动时自动执行
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // 根据需要决定是否自动执行迁移
        MigrationResult userResult = userMigrationService.migrateConsumerData();
        log.info("用户数据迁移完成: {}", userResult);

        MigrationResult websiteResult = websiteSettingMigrationService.migrateWebsiteSetting();
        log.info("网站设置迁移完成: {}", websiteResult);

        MigrationResult accessTokenResult = accessTokenMigrationService.accessTokenData();
        log.info("访问令牌迁移完成: {}", accessTokenResult);
    }

}

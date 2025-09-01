package com.jmal.clouddisk.dao.mongo_to_jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
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
    private final FileMigrationService fileMigrationService;
    private final TagMigrationService tagMigrationService;

    // 可以在应用启动时自动执行
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // 根据需要决定是否自动执行迁移
        MigrationResult userResult = userMigrationService.migrateConsumerData();
        if (userResult.getTotalProcessed() > 0) {
            log.info("用户数据迁移完成: {}", userResult);
        }

        MigrationResult websiteResult = websiteSettingMigrationService.migrateWebsiteSetting();
        if (websiteResult.getTotalProcessed() > 0) {
            log.info("网站设置迁移完成: {}", websiteResult);
        }

        MigrationResult accessTokenResult = accessTokenMigrationService.accessTokenData();
        if (accessTokenResult.getTotalProcessed() > 0) {
            log.info("访问令牌迁移完成: {}", accessTokenResult);
        }

        MigrationResult fileResult = fileMigrationService.migrateConsumerData();
        if (fileResult.getTotalProcessed() > 0) {
            log.info("文件数据迁移完成: {}", fileResult);
        }

        MigrationResult tagResult = tagMigrationService.migrateTagData();
        if (tagResult.getTotalProcessed() > 0) {
            log.info("标签数据迁移完成: {}", tagResult);
        }
    }

}

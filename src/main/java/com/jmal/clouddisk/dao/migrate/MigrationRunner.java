package com.jmal.clouddisk.dao.migrate;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.migration")
@Conditional(RelationalDataSourceCondition.class)
public class MigrationRunner {

    private final List<IMigrationService> migrationServices;

    // 可以在应用启动时自动执行
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {

        log.info("检测到关系型数据源配置，开始执行数据迁移任务...");

        // 将各个具体的迁移服务按类型注入
        migrationServices.parallelStream().forEach(service -> {
            MigrationResult result = service.migrateData();
            if (result.getTotalProcessed() == null || result.getTotalProcessed() == 0) {
                return;
            }
            if (!result.getTotalProcessed().equals(result.getSuccessCount())) {
                log.error("迁移 [{}] 数据时发生错误: 成功 {} 条, 失败 {} 条", result.getName(), result.getSuccessCount(), result.getErrorCount());
                if (result.getFatalError() != null) {
                    log.error("致命错误: {}", result.getFatalError());
                } else {
                    result.getErrors().forEach(log::error);
                }
            } else {
                log.info("成功迁移 {} 条 [{}] 数据, 耗时: {} 秒", result.getSuccessCount(), result.getName(), result.getDurationInSeconds());
            }
        });

    }

}

package com.jmal.clouddisk.dao.impl.jpa.migration;

import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.UserJpaRepository;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.migration")
@Conditional(RelationalDataSourceCondition.class)
public class UserMigrationService {

    private final MongoTemplate mongoTemplate;

    private final UserJpaRepository userJpaRepository;

    /**
     * 迁移用户数据从 MongoDB 到 SQLite
     */
    @Transactional
    public MigrationResult migrateConsumerData() {
        log.info("开始迁移用户数据从 MongoDB 到 SQLite");

        MigrationResult result = new MigrationResult();
        int batchSize = 1000; // 批量处理大小
        int skip = 0;

        try {
            while (true) {
                // 分批从 MongoDB 读取数据
                Query query = new Query().skip(skip).limit(batchSize);
                List<ConsumerDO> mongoConsumers = mongoTemplate.find(query, ConsumerDO.class);

                if (mongoConsumers.isEmpty()) {
                    break; // 没有更多数据
                }

                log.info("正在处理第 {} 批数据，数量: {}", (skip / batchSize) + 1, mongoConsumers.size());

                try {
                    // 直接批量保存到 SQLite，无需转换
                    userJpaRepository.saveAll(mongoConsumers);
                    result.addSuccess(mongoConsumers.size());
                    result.addProcessed(mongoConsumers.size());
                    log.info("成功保存 {} 条记录到 SQLite", mongoConsumers.size());
                } catch (Exception e) {
                    log.error("批量保存到 SQLite 失败: {}", e.getMessage());

                    // 如果批量保存失败，尝试逐条保存
                    for (ConsumerDO consumer : mongoConsumers) {
                        try {
                            userJpaRepository.save(consumer);
                            result.addSuccess(1);
                            result.incrementProcessed();
                        } catch (Exception ex) {
                            log.error("保存用户数据失败, ID: {}, 错误: {}", consumer.getId(), ex.getMessage());
                            result.addError(consumer.getId(), ex.getMessage());
                            result.incrementProcessed();
                        }
                    }
                }

                skip += batchSize;
            }

        } catch (Exception e) {
            log.error("迁移过程中发生错误: {}", e.getMessage(), e);
            result.setFatalError(e.getMessage());
        }

        return result;
    }

}

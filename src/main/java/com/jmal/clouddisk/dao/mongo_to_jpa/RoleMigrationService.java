package com.jmal.clouddisk.dao.mongo_to_jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.RoleRepository;
import com.jmal.clouddisk.model.rbac.RoleDO;
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
public class RoleMigrationService {

    private final MongoTemplate mongoTemplate;

    private final RoleRepository jpaRepository;

    /**
     * 迁移 Role 数据从 MongoDB 到 JPA
     */
    @Transactional
    public MigrationResult migrateRoleData() {
        if (jpaRepository.count() > 0) {
            return new MigrationResult();
        }

        log.info("开始迁移 Tag 数据从 MongoDB 到 JPA");

        MigrationResult result = new MigrationResult();
        int batchSize = 1000; // 批量处理大小
        int skip = 0;

        try {
            while (true) {
                // 分批从 MongoDB 读取数据
                Query query = new Query().skip(skip).limit(batchSize);
                List<RoleDO> mongoDataList = mongoTemplate.find(query, RoleDO.class);

                if (mongoDataList.isEmpty()) {
                    break; // 没有更多数据
                }

                log.debug("正在处理第 {} 批数据，数量: {}", (skip / batchSize) + 1, mongoDataList.size());

                try {

                    // 直接批量保存到 SQLite，无需转换
                    jpaRepository.saveAll(mongoDataList);

                    result.addSuccess(mongoDataList.size());
                    result.addProcessed(mongoDataList.size());
                    log.debug("成功保存 {} 条记录到 SQLite", mongoDataList.size());
                } catch (Exception e) {
                    log.error("批量保存到 SQLite 失败: {}", e.getMessage());

                    // 如果批量保存失败，尝试逐条保存
                    for (RoleDO mongoData : mongoDataList) {
                        try {
                            jpaRepository.save(mongoData);
                            result.addSuccess(1);
                            result.incrementProcessed();
                        } catch (Exception ex) {
                            log.error("保存 Role 数据失败, ID: {}, 错误: {}", mongoData.getId(), ex.getMessage());
                            result.addError(mongoData.getId(), ex.getMessage());
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

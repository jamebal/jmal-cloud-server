package com.jmal.clouddisk.dao.migrate;

import com.jmal.clouddisk.config.jpa.AuditableEntity;
import com.jmal.clouddisk.dao.DataSourceType;
import com.jmal.clouddisk.dao.impl.jpa.IWriteCommon;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * 数据迁移工具类
 */
@Slf4j
public final class MigrationUtils {

    private MigrationUtils() {}

    /**
     * 将数据从 MongoDB 分批迁移到 JPA 兼容的数据库（如 SQLite）。
     * <p>
     *
     * @param migrationName 迁移任务的名称，用于日志记录。
     * @param mongoTemplate 用于从 MongoDB 读取数据的 MongoTemplate 实例。
     * @param jpaRepository 用于将数据写入目标数据库的 CrudRepository 实例。
     * @param entityClass   要迁移的实体的 Class 对象，例如 UserAccessTokenDO.class。
     * @param batchSize     每批处理的数据量。
     * @param <T>           实体类型，必须继承自 AuditableEntity 以便在日志中获取ID。
     * @return MigrationResult 包含迁移的详细结果。
     */
    public static <T extends AuditableEntity> MigrationResult migrateMongoToJpa(
            DataSourceType dataSourceType,
            String migrationName,
            MongoTemplate mongoTemplate,
            CrudRepository<T, String> jpaRepository,
            IWriteCommon<T> iWriteCommon,
            Class<T> entityClass,
            int batchSize) {

        MigrationResult result = new MigrationResult(migrationName);

        // 1. 检查目标表是否已有数据，如果有则跳过
        if (jpaRepository.count() > 0) {
            return result;
        }

        log.debug("开始 [{}] 数据迁移: MongoDB -> {}", migrationName, dataSourceType.getDescription());
        int skip = 0;

        try {
            while (true) {
                // 2. 分批从 MongoDB 读取数据
                Query query = new Query().skip(skip).limit(batchSize);
                List<T> mongoDataList = mongoTemplate.find(query, entityClass);

                if (mongoDataList.isEmpty()) {
                    break; // 没有更多数据，迁移完成
                }

                log.debug("[{}] 正在处理第 {} 批数据，数量: {}", migrationName, (skip / batchSize) + 1, mongoDataList.size());
                result.addProcessed(mongoDataList.size());

                try {
                    // 3. 尝试批量保存到目标数据库
                    iWriteCommon.AsyncSaveAll(mongoDataList);
                    result.addSuccess(mongoDataList.size());
                    log.debug("[{}] 成功批量保存 {} 条记录", migrationName, mongoDataList.size());
                } catch (Exception e) {
                    log.warn("[{}] 批量保存失败，将回退到逐条保存。错误: {}", migrationName, e.getMessage());
                }
                skip += batchSize;
            }

        } catch (Exception e) {
            log.error("[{}] 迁移过程中发生严重错误: {}", migrationName, e.getMessage(), e);
            result.setFatalError(e.getMessage());
        }

        return result;
    }
}

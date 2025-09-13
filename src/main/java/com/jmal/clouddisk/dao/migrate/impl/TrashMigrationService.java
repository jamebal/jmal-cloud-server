package com.jmal.clouddisk.dao.migrate.impl;

import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.TrashRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.trash.TrashOperation;
import com.jmal.clouddisk.dao.migrate.IMigrationService;
import com.jmal.clouddisk.dao.migrate.MigrationResult;
import com.jmal.clouddisk.model.Trash;
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
public class TrashMigrationService implements IMigrationService {

    private final MongoTemplate mongoTemplate;

    private final TrashRepository trashRepository;

    private final IWriteService writeService;

    private final DataSourceProperties dataSourceProperties;

    @Override
    public String getName() {
        return "回收站";
    }

    /**
     * 迁移 回收站 数据从 MongoDB 到 JPA
     */
    @Override
    public MigrationResult migrateData() {
        MigrationResult result = new MigrationResult("回收站");
        if (trashRepository.count() > 0) {
            return result;
        }

        log.debug("开始 [{}] 数据迁移: MongoDB -> {}", getName(), dataSourceProperties.getType().getDescription());


        int batchSize = 1000; // 批量处理大小
        int skip = 0;

        try {
            while (true) {
                // 分批从 MongoDB 读取数据
                Query query = new Query().skip(skip).limit(batchSize);
                List<Trash> mongoDataList = mongoTemplate.find(query, Trash.class);

                if (mongoDataList.isEmpty()) {
                    break; // 没有更多数据
                }

                log.debug("[{}] 正在处理第 {} 批数据，数量: {}", getName(), (skip / batchSize) + 1, mongoDataList.size());

                try {

                    writeService.submit(new TrashOperation.CreateAll(mongoDataList));

                    result.addSuccess(mongoDataList.size());
                    result.addProcessed(mongoDataList.size());
                    log.debug("[{}] 成功批量保存 {} 条记录", getName(), mongoDataList.size());
                } catch (Exception e) {
                    log.warn("[{}] 批量保存失败，将回退到逐条保存。错误: {}", getName(), e.getMessage());
                }

                skip += batchSize;
            }

        } catch (Exception e) {
            log.error("[{}] 迁移过程中发生严重错误: {}", getName(), e.getMessage(), e);
            result.setFatalError(e.getMessage());
        }

        return result;
    }

}

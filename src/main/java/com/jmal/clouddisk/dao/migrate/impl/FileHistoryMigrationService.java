package com.jmal.clouddisk.dao.migrate.impl;

import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.FilePersistenceService;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileHistoryRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.filehistory.FileHistoryOperation;
import com.jmal.clouddisk.dao.migrate.IMigrationService;
import com.jmal.clouddisk.dao.migrate.MigrationResult;
import com.jmal.clouddisk.model.GridFSBO;
import com.jmal.clouddisk.model.file.FileHistoryDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.migration")
@Conditional(RelationalDataSourceCondition.class)
public class FileHistoryMigrationService implements IMigrationService {

    private final MongoTemplate mongoTemplate;

    private final GridFsTemplate gridFsTemplate;

    private final FileHistoryRepository fileHistoryRepository;

    private final FilePersistenceService filePersistenceService;

    private final IWriteService writeService;

    private final DataSourceProperties dataSourceProperties;

    @Override
    public String getName() {
        return "文件历史";
    }

    /**
     * 迁移 回收站 数据从 MongoDB 到 JPA
     */
    @Override
    public MigrationResult migrateData() {
        MigrationResult result = new MigrationResult(getName());
        if (fileHistoryRepository.count() > 0) {
            return result;
        }

        log.debug("开始 [{}] 数据迁移: MongoDB -> {}", getName(), dataSourceProperties.getType().getDescription());


        int batchSize = 1000; // 批量处理大小
        int skip = 0;

        try {
            while (true) {
                // 分批从 MongoDB 读取数据
                Query query = new Query().skip(skip).limit(batchSize);
                List<GridFSBO> gridFSBOList = mongoTemplate.find(query, GridFSBO.class, "fs.files");

                if (gridFSBOList.isEmpty()) {
                    break; // 没有更多数据
                }

                log.debug("[{}] 正在处理第 {} 批数据，数量: {}", getName(), (skip / batchSize) + 1, gridFSBOList.size());

                try {
                    List<FileHistoryDO> fileHistoryDOList = gridFSBOList.stream().map(FileHistoryDO::new).toList();

                    writeService.submit(new FileHistoryOperation.CreateAll(fileHistoryDOList));



                    gridFSBOList.forEach(item -> {
                        try (InputStream inputStream = gridFsTemplate.getResource(item.getFilename()).getInputStream()) {
                            filePersistenceService.persistFileHistory(item.getFilename(), inputStream, item.getId());
                        } catch (Exception e) {
                            log.error("迁移文件历史文件失败, fileHistoryId: {}, fileId: {}, error: {}", item.getId(), item.getId(), e.getMessage(), e);
                        }
                    });
                    result.addSuccess(fileHistoryDOList.size());
                    result.addProcessed(fileHistoryDOList.size());
                    log.debug("[{}] 成功批量保存 {} 条记录", getName(), fileHistoryDOList.size());
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

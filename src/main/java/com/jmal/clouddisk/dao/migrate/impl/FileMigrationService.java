package com.jmal.clouddisk.dao.migrate.impl;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.config.jpa.DataSourceProperties;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.article.ArticleOperation;
import com.jmal.clouddisk.dao.impl.jpa.write.file.FileOperation;
import com.jmal.clouddisk.dao.migrate.IMigrationService;
import com.jmal.clouddisk.dao.migrate.MigrationResult;
import com.jmal.clouddisk.model.file.ArticleDO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import com.jmal.clouddisk.service.impl.FilePersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.migration")
@Conditional(RelationalDataSourceCondition.class)
public class FileMigrationService implements IMigrationService {

    private final MongoTemplate mongoTemplate;

    private final FileMetadataRepository fileMetadataRepository;

    private final IWriteService writeService;

    private final FilePersistenceService filePersistenceService;

    private final DataSourceProperties dataSourceProperties;

    private final FileProperties fileProperties;

    private int articlesCount;

    @Override
    public String getName() {
        return "文件";
    }

    /**
     * 迁移 File 数据从 MongoDB 到 JPA
     */
    @Override
    public MigrationResult migrateData() {
        MigrationResult result = new MigrationResult("文件");
        if (fileMetadataRepository.count() > 0) {
            return result;
        }

        log.debug("开始 [{}] 数据迁移: MongoDB -> {}", getName(), dataSourceProperties.getType().getDescription());


        int batchSize = 1000; // 批量处理大小
        int skip = 0;

        try {
            while (true) {
                // 分批从 MongoDB 读取数据
                Query query = new Query().skip(skip).limit(batchSize);
                List<FileDocument> mongoDataList = mongoTemplate.find(query, FileDocument.class);

                if (mongoDataList.isEmpty()) {
                    break; // 没有更多数据
                }

                log.debug("[{}] 正在处理第 {} 批数据，数量: {}", getName(), (skip / batchSize) + 1, mongoDataList.size());

                try {

                    List<ArticleDO> articleDOList = new ArrayList<>();

                    // 转换 FileDocument 到 FileEntityDO
                    List<FileMetadataDO> fileEntityDOList = mongoDataList.stream().map(fileDocument -> {
                        FileMetadataDO fileMetadataDO = new FileMetadataDO(fileDocument);
                        filePersistenceService.persistContents(fileDocument);
                        if (fileDocument.getSlug() != null || (fileDocument.getPath().startsWith(fileProperties.getDocumentDir()) && "md".equals(fileDocument.getSuffix()))) {
                            ArticleDO articleDO = new ArticleDO(fileDocument);
                            articleDO.setFileMetadata(fileMetadataDO);
                            articleDOList.add(articleDO);
                        }
                        return fileMetadataDO;
                    }).toList();

                    // 直接批量保存到 SQLite，无需转换
                    writeService.submit(new FileOperation.CreateAllFileMetadata(fileEntityDOList));

                    // 保存文章
                    if (!articleDOList.isEmpty()) {
                        writeService.submit(new ArticleOperation.CreateAll(articleDOList));
                        articlesCount += articleDOList.size();
                    }
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

        log.info("成功迁移 {} 条 [文章] 数据",  articlesCount);

        return result;
    }

}

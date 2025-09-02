package com.jmal.clouddisk.dao.mongo_to_jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.ArticleRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.model.file.ArticleDO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import jakarta.transaction.Transactional;
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
public class FileMigrationService {

    private final MongoTemplate mongoTemplate;

    private final FileMetadataRepository fileMetadataRepository;

    private final ArticleRepository articleRepository;

    /**
     * 迁移 File 数据从 MongoDB 到 JPA
     */
    @Transactional
    public MigrationResult migrateConsumerData() {
        if (fileMetadataRepository.count() > 0) {
            return new MigrationResult();
        }

        log.info("开始迁移 File 数据从 MongoDB 到 JPA");

        MigrationResult result = new MigrationResult();
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

                log.debug("正在处理第 {} 批数据，数量: {}", (skip / batchSize) + 1, mongoDataList.size());

                try {

                    List<ArticleDO> articleDOList = new ArrayList<>();

                    // 转换 FileDocument 到 FileEntityDO
                    List<FileMetadataDO> fileEntityDOList = mongoDataList.stream().map(fileDocument -> {
                        FileMetadataDO fileMetadataDO = new FileMetadataDO(fileDocument);
                        if (fileDocument.getSlug() != null) {
                            ArticleDO articleDO = new ArticleDO(fileDocument);
                            articleDO.setFileMetadata(fileMetadataDO);
                            articleDOList.add(articleDO);
                        }
                        return fileMetadataDO;
                    }).toList();


                    // 直接批量保存到 SQLite，无需转换
                    fileMetadataRepository.saveAll(fileEntityDOList);

                    // 保存文章
                    if (!articleDOList.isEmpty()) {
                        articleRepository.saveAll(articleDOList);
                    }
                    result.addSuccess(mongoDataList.size());
                    result.addProcessed(mongoDataList.size());
                    log.debug("成功保存 {} 条记录到 SQLite", mongoDataList.size());
                } catch (Exception e) {
                    log.error("批量保存到 SQLite 失败: {}", e.getMessage());

                    // 如果批量保存失败，尝试逐条保存
                    for (FileDocument mongoData : mongoDataList) {
                        try {
                            fileMetadataRepository.save(new FileMetadataDO(mongoData));
                            result.addSuccess(1);
                            result.incrementProcessed();
                        } catch (Exception ex) {
                            log.error("保存 File 数据失败, ID: {}, 错误: {}", mongoData.getId(), ex.getMessage());
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

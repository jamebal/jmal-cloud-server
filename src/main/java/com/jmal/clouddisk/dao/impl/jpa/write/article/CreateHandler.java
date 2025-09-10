package com.jmal.clouddisk.dao.impl.jpa.write.article;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.ArticleRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import com.jmal.clouddisk.model.file.ArticleDO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("articleCreateHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateHandler implements IDataOperationHandler<ArticleOperation.Create, ArticleDO> {

    private final ArticleRepository repo;

    private final FileMetadataRepository fileMetadataRepository;

    @Override
    public ArticleDO handle(ArticleOperation.Create op) {
        FileDocument fileDocument = op.fileDocument();
        String fileId = fileDocument.getId();

        Optional<ArticleDO> existingArticleOpt = repo.findById(fileId);

        if (existingArticleOpt.isPresent()) {
            // --- 更新逻辑 ---
            ArticleDO articleToUpdate = existingArticleOpt.get();
            FileMetadataDO metadataToUpdate = articleToUpdate.getFileMetadata();

            articleToUpdate.convert(fileDocument);
            metadataToUpdate.covert(fileDocument);
            return articleToUpdate;

        } else {
            // --- 创建逻辑 ---
            FileMetadataDO newMetadata = new FileMetadataDO(fileDocument);
            ArticleDO newArticle = new ArticleDO(fileDocument);

            // 设置关系 (拥有方设置)
            newArticle.setFileMetadata(newMetadata);
            return repo.save(newArticle);
        }
    }
}

package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.dao.IArticleDAO;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.ArticleRepository;
import com.jmal.clouddisk.dao.impl.jpa.repository.FileMetadataRepository;
import com.jmal.clouddisk.model.file.ArticleDO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class ArticleDAOJpaImpl implements IArticleDAO {

    private final ArticleRepository articleRepository;
    private final FileMetadataRepository fileMetadataRepository;

    @Override
    public void createArticleFromDocument(FileDocument fileDocument) {
        FileMetadataDO existingFile = fileMetadataRepository.findById(fileDocument.getId())
                .orElseThrow(() -> new EntityNotFoundException("FileMetadataDO not found with id: " + fileDocument.getId()));

        ArticleDO newArticle = new ArticleDO(fileDocument);

        newArticle.setFileMetadata(existingFile);

        articleRepository.save(newArticle);
    }
}

package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.file.ArticleDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface ArticleRepository extends JpaRepository<ArticleDO, String> {


    @Query("SELECT a FROM ArticleDO a " +
            "JOIN FETCH a.fileMetadata fm " +
            "JOIN FETCH a.fileMetadata.props fp " +
            "WHERE a.fileMetadata.id = :fileId")
    Optional<ArticleDO> findMarkdownByFileId(@Param("fileId") String fileId);

    @Query("SELECT new ArticleDO(a.id, a.alonePage, a.slug, f.updateDate) FROM ArticleDO a " +
            "JOIN a.fileMetadata f " +
            "WHERE a.release = true")
    List<ArticleDO> findByReleaseIsTrue();

}

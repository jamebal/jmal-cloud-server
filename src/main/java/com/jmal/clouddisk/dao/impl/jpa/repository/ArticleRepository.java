package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.file.ArticleDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface ArticleRepository extends JpaRepository<ArticleDO, String> {

    /**
     * 计算引用了特定 tagId 并且已发布的文章数量。
     *
     * @param tagIdJson 要查询的标签ID。
     * @return 满足条件的文章数量。
     */
    @Query("SELECT COUNT(a) FROM ArticleDO a " +
            "WHERE a.release = true AND function('JSON_CONTAINS', a.tagIds, :tagIdJson) = 1")
    long countByTagIdAndReleased(@Param("tagIdJson") String tagIdJson);

}

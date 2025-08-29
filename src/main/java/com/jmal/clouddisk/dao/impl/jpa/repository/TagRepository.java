package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.TagDO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface TagRepository extends JpaRepository<TagDO, String>, JpaSpecificationExecutor<ConsumerDO> {

    @Query("SELECT t FROM TagDO t WHERE (:userId IS NULL AND t.userId IS NULL) OR (t.userId = :userId)")
    List<TagDO> findTagsByUserIdOrNull(@Param("userId") String userId);

    @Query("SELECT t FROM TagDO t WHERE ((:userId IS NULL AND t.userId IS NULL) OR (t.userId = :userId)) AND t.name = :name")
    Optional<TagDO> findOneTagByUserIdAndName(@Param("userId") String userId, @Param("name") String name);

    @Query("SELECT t FROM TagDO t WHERE ((:userId IS NULL AND t.userId IS NULL) OR (t.userId = :userId)) AND t.slug = :slug")
    Optional<TagDO> findOneTagByUserIdAndSlug(@Param("userId") String userId, @Param("slug") String slug);

}

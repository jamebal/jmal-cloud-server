package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.TagDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface TagRepository extends JpaRepository<TagDO, String> {

    @Query("SELECT t FROM TagDO t WHERE (:userId IS NULL AND t.userId IS NULL) OR (t.userId = :userId)")
    List<TagDO> findTagsByUserIdOrNull(@Param("userId") String userId);

    @Query("SELECT t FROM TagDO t WHERE ((:userId IS NULL AND t.userId IS NULL) OR (t.userId = :userId)) AND t.name = :name")
    Optional<TagDO> findOneTagByUserIdAndName(@Param("userId") String userId, @Param("name") String name);

    @Query("SELECT t FROM TagDO t WHERE ((:userId IS NULL AND t.userId IS NULL) OR (t.userId = :userId)) AND t.slug = :slug")
    Optional<TagDO> findOneTagByUserIdAndSlug(@Param("userId") String userId, @Param("slug") String slug);

    boolean existsByNameAndIdNot(String name, String id);

    boolean existsBySlug(String slug);
    boolean existsBySlugAndIdNot(String slug, String id);

    void removeByIdIn(List<String> idList);

    @Modifying
    @Query("UPDATE TagDO t SET t.sort = :sort WHERE t.id = :id")
    void updateSortById(String id, Integer sort);
}

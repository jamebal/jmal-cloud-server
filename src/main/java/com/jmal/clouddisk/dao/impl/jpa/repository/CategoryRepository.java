package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.CategoryDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface CategoryRepository extends JpaRepository<CategoryDO, String>, JpaSpecificationExecutor<CategoryDO> {

    List<CategoryDO> findByUserId(String userId);

    List<CategoryDO> findByUserIdIsNull();

    Optional<CategoryDO> findByUserIdAndName(String userId, String name);

    Optional<CategoryDO> findByUserIdIsNullAndName(String name);

    Optional<CategoryDO> findByUserIdAndSlug(String userId, String slug);

    Optional<CategoryDO> findByUserIdIsNullAndSlug(String slug);

    boolean existsByNameAndIdIsNot(String name, String id);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdIsNot(String slug, String id);

    @Modifying
    @Query("update CategoryDO c set c.isDefault = false where c.isDefault = true")
    void updateSetDefaultFalseByDefaultIsTrue();

    @Modifying
    @Query("update CategoryDO c set c.isDefault = true where c.id = :categoryId")
    void updateSetDefaultTrueById(String categoryId);

    void deleteAllByIdIn(Collection<String> categoryIds);

}

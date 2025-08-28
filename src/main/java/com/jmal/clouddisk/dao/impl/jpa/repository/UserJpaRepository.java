package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface UserJpaRepository extends JpaRepository<ConsumerDO, String>, JpaSpecificationExecutor<ConsumerDO> {


    Optional<ConsumerDO> findByUsername(String username);

    Optional<ConsumerDO> findOneByCreatorTrue();

    /**
     * 使用EntityGraph优化关联对象加载
     */
    @NotNull
    @EntityGraph(attributePaths = {"roles"})
    Page<ConsumerDO> findAll(Specification<ConsumerDO> spec, @NotNull Pageable pageable);

    @EntityGraph(attributePaths = {"roles"})
    @Query("SELECT c FROM ConsumerDO c " +
            "WHERE (:username IS NULL OR LOWER(c.username) LIKE LOWER(CONCAT('%', :username, '%'))) " +
            "AND (:showName IS NULL OR LOWER(c.showName) LIKE LOWER(CONCAT('%', :showName, '%')))")
    Page<ConsumerDO> findUserList(
            @Param("username") String username,
            @Param("showName") String showName,
            Pageable pageable);

    Optional<ConsumerDO> findByShowName(String showName);
}

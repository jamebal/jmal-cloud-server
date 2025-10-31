package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface UserRepository extends JpaRepository<ConsumerDO, String>, JpaSpecificationExecutor<ConsumerDO> {

    Optional<ConsumerDO> findByUsername(String username);

    Optional<ConsumerDO> findOneByCreatorTrue();

    @Query(value = "SELECT c FROM ConsumerDO c " +
            "WHERE (:username IS NULL OR :username = '' OR LOWER(c.username) LIKE LOWER(CONCAT('%', :username, '%'))) " +
            "AND (:showName IS NULL OR :showName = '' OR LOWER(c.showName) LIKE LOWER(CONCAT('%', :showName, '%')))")
    Page<ConsumerDO> findUserList(@Param("username") String username,
                                  @Param("showName") String showName,
                                  Pageable pageable);

    Optional<ConsumerDO> findByShowName(String showName);

    /**
     * 根据 roleId 列表查询所有匹配的用户名 (PostgreSQL 版本)
     * 使用 jsonb_exists_any 函数
     * JPA/Hibernate 会自动将 List<String> 绑定为 PostgreSQL 的数组类型。
     *
     * @param roleIdList 要查询的角色ID列表
     * @return 匹配的用户名列表
     */
    @Query(value = "SELECT username FROM consumers WHERE jsonb_exists_any(roles, :roleIdList)",
            nativeQuery = true)
    List<String> findUsernamesByRoleIdList_PostgreSQL(@Param("roleIdList") String[] roleIdList);

    /**
     * 根据 roleId 列表查询所有匹配的用户名 (MySQL 版本)
     * 使用 JSON_OVERLAPS 函数，它检查两个JSON数组是否有共同元素。
     *
     * @param roleIdListAsJson 一个已经格式化为JSON数组的字符串, 例如 "[\"role1\", \"role2\"]"
     * @return 匹配的用户名列表
     */
    @Query(value = "SELECT username FROM consumers WHERE JSON_OVERLAPS(roles, :roleIdListAsJson)",
            nativeQuery = true)
    List<String> findUsernamesByRoleIdList_MySQL(@Param("roleIdListAsJson") String roleIdListAsJson);

    /**
     * 根据 roleId 列表查询所有匹配的用户名 (SQLite 版本)
     * 将 roles 数组展开，然后使用标准的 IN 子句来匹配列表中的任何一个roleId。
     * 必须使用 DISTINCT，因为一个用户可能匹配列表中的多个角色，这会导致用户名重复。
     *
     * @param roleIdList 要查询的角色ID列表
     * @return 匹配的用户名列表
     */
    @Query(value = "SELECT DISTINCT c.username FROM consumers c, json_each(c.roles) je WHERE je.value IN (:roleIdList)",
            nativeQuery = true)
    List<String> findUsernamesByRoleIdList_SQLite(@Param("roleIdList") Collection<String> roleIdList);

    @Query("update ConsumerDO c set c.password = :password where c.creator = true")
    @Modifying
    int updatePasswordByCreatorTrue(String password);

    @Query("update ConsumerDO c set c.mfaEnabled = null, c.mfaSecret = null")
    @Modifying
    void resetMfaForAllUsers();
}

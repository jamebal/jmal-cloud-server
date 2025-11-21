package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.rbac.GroupDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface GroupRepository extends JpaRepository<GroupDO, String>, JpaSpecificationExecutor<GroupDO> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, String id);

    List<GroupDO> findAllByIdIn(List<String> ids);

    @Query("SELECT g.roles FROM GroupDO g WHERE g.id IN :groupIds")
    List<List<String>> findRolesByGroupIds(@Param("groupIds") List<String> groupIds);

    @Query(value = "SELECT * FROM user_groups WHERE jsonb_exists_any(roles, :roleIdList)",
            nativeQuery = true)
    List<GroupDO> findAllByRoleIdList_PostgreSQL(@Param("roleIdList") String[] roleIdList);

    @Query(value = "SELECT * FROM user_groups WHERE JSON_OVERLAPS(roles, :roleIdListAsJson)",
            nativeQuery = true)
    List<GroupDO> findAllByRoleIdList_MySQL(@Param("roleIdListAsJson") String roleIdListAsJson);

    @Query(value = "SELECT DISTINCT * FROM user_groups g, json_each(g.roles) je WHERE je.value IN (:roleIdList)",
            nativeQuery = true)
    List<GroupDO> findAllByRoleIdList_SQLite(@Param("roleIdList") Collection<String> roleIdList);
}

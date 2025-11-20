package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.model.rbac.GroupDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GroupRepository extends JpaRepository<GroupDO, String>, JpaSpecificationExecutor<GroupDO> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, String id);

    List<GroupDO> findAllByIdIn(List<String> ids);

    @Query("SELECT g.roles FROM GroupDO g WHERE g.id IN :groupIds")
    List<List<String>> findRolesByGroupIds(@Param("groupIds") List<String> groupIds);
}

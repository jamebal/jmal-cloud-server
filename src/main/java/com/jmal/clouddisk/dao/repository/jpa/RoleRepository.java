package com.jmal.clouddisk.dao.repository.jpa;

import com.jmal.clouddisk.model.rbac.RoleDO;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface RoleRepository extends JpaRepository<RoleDO, String>, JpaSpecificationExecutor<RoleDO> {

    boolean existsByCode(String code);

    boolean existsById(@NotNull String id);

    boolean existsByCodeAndIdNot(String code, String id);

    void removeByIdIn(List<String> roleIdList);

    RoleDO findByCode(String code);

    Set<RoleDO> findAllByIdIn(Collection<String> ids);

    @Query("SELECT r.menuIds from RoleDO r where r.id in :ids")
    Set<List<String>> findMenuIdsByIdIn(Collection<String> ids);
}

package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.rbac.RoleDO;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface RoleRepository extends JpaRepository<RoleDO, String>, JpaSpecificationExecutor<RoleDO> {

    boolean existsByCode(String code);

    boolean existsById(@NotNull String id);

    boolean existsByCodeAndIdNot(String code, String id);

    void removeByIdIn(List<String> roleIdList);

    List<RoleDO> findAllByIdIn(List<String> roleIdList);

    RoleDO findByCode(String code);
}

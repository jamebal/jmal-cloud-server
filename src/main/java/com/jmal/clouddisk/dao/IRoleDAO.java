package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.query.QueryRoleDTO;
import com.jmal.clouddisk.model.rbac.RoleDO;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;

import java.util.List;

public interface IRoleDAO {

    Page<RoleDO> page(QueryRoleDTO queryDTO);

    boolean existsByCode(String roleCode);

    boolean existsById(String roleId);

    void save(RoleDO roleDO);

    boolean existsByCodeAndIdNot(@NotNull(message = "角色标识不能为空") String code, String id);

    void removeByIdIn(List<String> roleIdList);

    List<RoleDO> findAllByIdIn(List<String> roleIdList);

    RoleDO findByCode(String roleCode);

    void saveAll(List<RoleDO> roleDOList);
}

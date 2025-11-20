package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.model.rbac.GroupDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Set;

public interface GroupRepository extends JpaRepository<GroupDO, String>, JpaSpecificationExecutor<GroupDO> {

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, String id);

    Set<GroupDO> findAllByIdIn(List<String> ids);
}

package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.rbac.MenuDO;
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
public interface MenuRepository extends JpaRepository<MenuDO, String>, JpaSpecificationExecutor<MenuDO> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, String id);

    @Query("SELECT m.id FROM MenuDO m WHERE m.parentId IN :parentIds")
    List<String> findIdsByParentIdIn(@Param("parentIds") Collection<String> parentIds);

    void removeByIdIn(Collection<String> idList);

    @Query("SELECT m.authority FROM MenuDO m WHERE m.id IN :ids")
    List<String> findAuthorityAllByIds(List<String> ids);

    @Query("SELECT m.id FROM MenuDO m")
    List<String> findIdsAll();
}

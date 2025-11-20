package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.query.QueryGroupDTO;
import com.jmal.clouddisk.model.rbac.GroupDO;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Optional;

public interface IGroupDAO {

    Page<GroupDO> page(QueryGroupDTO queryGroupDTO);

    boolean existsByCode(String groupCode);

    void save(GroupDO groupDO);

    /**
     * 根据code判断是否存在，排除指定ID（用于更新时的排重）
     */
    boolean existsByCodeAndIdNot(@NotNull(message = "组标识不能为空") String code, String id);

    void removeByIdIn(List<String> groupIds);

    List<GroupDO> findAllByIdIn(List<String> groupIds);

    void saveAll(List<GroupDO> groupDOList);

    Optional<GroupDO> findById(String id);

}

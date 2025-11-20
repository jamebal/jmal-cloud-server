package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IGroupDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.GroupRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.group.GroupOperation;
import com.jmal.clouddisk.dao.util.PageableUtil;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.query.QueryGroupDTO;
import com.jmal.clouddisk.model.rbac.GroupDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class GroupDAOJpaImpl implements IGroupDAO, IWriteCommon<GroupDO> {

    private final GroupRepository groupRepository;

    private final IWriteService writeService;

    @Override
    public Page<GroupDO> page(QueryGroupDTO queryGroupDTO) {
        Specification<GroupDO> spec = (_, _, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (!CharSequenceUtil.isBlank(queryGroupDTO.getName())) {
            spec = spec.and(likeContainsIgnoreCase("name", queryGroupDTO.getName()));
        }

        if (!CharSequenceUtil.isBlank(queryGroupDTO.getCode())) {
            spec = spec.and(likeContainsIgnoreCase("code", queryGroupDTO.getCode()));
        }

        Pageable pageable = PageableUtil.buildPageable(queryGroupDTO);
        return groupRepository.findAll(spec, pageable);
    }

    @Override
    public boolean existsByCode(String groupCode) {
        return groupRepository.existsByCode(groupCode);
    }

    @Override
    public void save(GroupDO groupDO) {
        try {
            writeService.submit(new GroupOperation.Create(groupDO)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Save GroupDO failed: {}", e.getMessage(), e);
            throw new CommonException("保存失败，原因: " + e.getMessage());
        }
    }

    @Override
    public boolean existsByCodeAndIdNot(String code, String id) {
        return groupRepository.existsByCodeAndIdNot(code, id);
    }

    @Override
    public void removeByIdIn(List<String> groupIds) {
        try {
            writeService.submit(new GroupOperation.RemoveByIdIn(groupIds)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Remove GroupDO by id list failed: {}", e.getMessage(), e);
            throw new CommonException("删除失败，原因: " + e.getMessage());
        }
    }

    @Override
    public List<GroupDO> findAllByIdIn(List<String> groupIds) {
        Set<GroupDO> groupDOSet = groupRepository.findAllByIdIn(groupIds);
        return new ArrayList<>(groupDOSet);
    }

    @Override
    public void saveAll(List<GroupDO> groupDOList) {
        try {
            writeService.submit(new GroupOperation.CreateAll(groupDOList)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Save all GroupDO failed: {}", e.getMessage(), e);
            throw new CommonException("批量保存失败，原因: " + e.getMessage());
        }
    }

    @Override
    public Optional<GroupDO> findById(String id) {
        return groupRepository.findById(id);
    }

    /**
     * 根据 field 进行不区分大小写的模糊查询
     */
    public static Specification<GroupDO> likeContainsIgnoreCase(String field, String keyword) {
        return (root, _, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get(field)), "%" + keyword.toLowerCase() + "%");
    }

    @Override
    public void AsyncSaveAll(Iterable<GroupDO> entities) {
        List<GroupDO> list = new ArrayList<>();
        entities.forEach(list::add);
        writeService.submit(new GroupOperation.CreateAll(list));
    }
}

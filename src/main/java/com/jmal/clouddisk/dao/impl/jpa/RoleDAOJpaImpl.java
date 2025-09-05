package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IRoleDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.RoleRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.role.RoleOperation;
import com.jmal.clouddisk.dao.util.PageableUtil;
import com.jmal.clouddisk.model.query.QueryRoleDTO;
import com.jmal.clouddisk.model.rbac.RoleDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class RoleDAOJpaImpl implements IRoleDAO, IWriteCommon<RoleDO> {

    private final RoleRepository roleRepository;

    private final IWriteService writeService;

    @Override
    @Transactional(readOnly = true)
    public Page<RoleDO> page(QueryRoleDTO queryDTO) {
        Specification<RoleDO> spec = (_, _, criteriaBuilder) -> criteriaBuilder.conjunction();

        if (!CharSequenceUtil.isBlank(queryDTO.getName())) {
            spec = spec.and(likeContainsIgnoreCase("name", queryDTO.getName()));
        }

        if (!CharSequenceUtil.isBlank(queryDTO.getCode())) {
            spec = spec.and(likeContainsIgnoreCase("code", queryDTO.getCode()));
        }

        if (!CharSequenceUtil.isBlank(queryDTO.getRemarks())) {
            spec = spec.and(likeContainsIgnoreCase("remarks", queryDTO.getRemarks()));
        }
        Pageable pageable = PageableUtil.buildPageable(queryDTO);
        return roleRepository.findAll(spec, pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCode(String roleCode) {
        return roleRepository.existsByCode(roleCode);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsById(String roleId) {
        return roleRepository.existsById(roleId);
    }

    @Override
    @Transactional
    public void save(RoleDO roleDO) {
        roleRepository.save(roleDO);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByCodeAndIdNot(String code, String id) {
        return roleRepository.existsByCodeAndIdNot(code, id);
    }

    @Override
    @Transactional
    public void removeByIdIn(List<String> roleIdList) {
        roleRepository.removeByIdIn(roleIdList);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleDO> findAllByIdIn(List<String> roleIdList) {
        Set<RoleDO> roleDOSet = roleRepository.findAllByIdIn(roleIdList);
        return new ArrayList<>(roleDOSet);
    }

    @Override
    @Transactional(readOnly = true)
    public RoleDO findByCode(String roleCode) {
        return roleRepository.findByCode(roleCode);
    }

    @Override
    public void saveAll(List<RoleDO> roleDOList) {
        roleRepository.saveAll(roleDOList);
    }

    /**
     * 根据 filed 进行不区分大小写的模糊查询 (keyword 不为空)
     */
    public static Specification<RoleDO> likeContainsIgnoreCase(String filed, String keyword) {
        return (root, _, criteriaBuilder) ->
                criteriaBuilder.like(criteriaBuilder.lower(root.get(filed)), "%" + keyword.toLowerCase() + "%");
    }

    @Override
    public void AsyncSaveAll(Iterable<RoleDO> entities) {
        writeService.submit(new RoleOperation.CreateAll(entities));
    }
}

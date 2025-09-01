package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.convert.Convert;
import com.jmal.clouddisk.dao.IUserDAO;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.UserRepository;
import com.jmal.clouddisk.dao.mapping.UserField;
import com.jmal.clouddisk.dao.util.MyQuery;
import com.jmal.clouddisk.dao.util.MyUpdate;
import com.jmal.clouddisk.dao.util.PageableUtil;
import com.jmal.clouddisk.dao.util.QuerySpecificationUtil;
import com.jmal.clouddisk.model.query.QueryUserDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UserDAOJpaImpl implements IUserDAO {

    private final UserRepository userRepository;

    @Override
    public ConsumerDO save(ConsumerDO consumerDO) {
        return userRepository.save(consumerDO);
    }

    @Override
    public List<ConsumerDO> findAllById(List<String> idList) {
        return userRepository.findAllById(idList);
    }

    @Override
    public void deleteAllById(List<String> idList) {
        userRepository.deleteAllById(idList);
    }

    @Override
    @Transactional(readOnly = true)
    public ConsumerDO findById(String userId) {
        return userRepository.findById(userId).orElse(null);
    }

    @Override
    @Transactional
    public void upsert(MyQuery myQuery, MyUpdate myUpdate) {

        Specification<ConsumerDO> spec = QuerySpecificationUtil.toSpecification(myQuery, UserField.allFields());

        Optional<ConsumerDO> optional = userRepository.findOne(spec);

        ConsumerDO entity;

        if (optional.isPresent()) {
            entity = optional.get();
        } else {
            entity = new ConsumerDO();
            applyQueryToEntity(entity, myQuery);
        }
        applyUpdateToEntity(entity, myUpdate);
        userRepository.save(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public ConsumerDO findByUsername(String username) {
        return userRepository.findByUsername(username).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public ConsumerDO findOneByCreatorTrue() {
        return userRepository.findOneByCreatorTrue().orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        return userRepository.count();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ConsumerDO> findUserList(QueryUserDTO queryDTO) {
        return userRepository.findUserList(queryDTO.getUsername(), queryDTO.getShowName(), PageableUtil.buildPageable(queryDTO));
    }

    @Override
    @Transactional(readOnly = true)
    public ConsumerDO findByShowName(String showName) {
        return userRepository.findByShowName(showName).orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public String getUsernameById(String userId) {
        ConsumerDO consumerDO = findById(userId);
        if (consumerDO != null) {
            return consumerDO.getUsername();
        }
        return null;
    }

    public void applyUpdateToEntity(ConsumerDO entity, MyUpdate update) {
        // 处理 set 操作
        for (var entry : update.getOperations().entrySet()) {
            setConsumerField(entity, entry.getKey(), entry.getValue());
        }
    }

    public void applyQueryToEntity(ConsumerDO entity, MyQuery query) {
        for (var entry : query.getEqMap().entrySet()) {
            setConsumerField(entity, entry.getKey(), entry.getValue());
        }
    }

    private static void setConsumerField(ConsumerDO entity, String logicalFieldName, Object value) {
        try {
            // 检查是否为 unset 操作
            boolean isUnset = (value == MyUpdate.UNSET);
            switch (logicalFieldName) {
                case "id" -> entity.setId(Convert.toStr(value));
                case "mfaEnabled" -> entity.setMfaEnabled(isUnset ? null : Convert.toBool(value));
                case "quota" -> entity.setQuota(isUnset ? null : Convert.toInt(value));
                case "webpDisabled" -> entity.setWebpDisabled(isUnset ? null : Convert.toBool(value));
                case "createdTime" -> entity.setCreatedTime(isUnset ? null : Convert.toLocalDateTime(value));
                case "takeUpSpace" -> entity.setTakeUpSpace(isUnset ? null : Convert.toLong(value));
                case "updatedTime" -> entity.setUpdatedTime(isUnset ? null : Convert.toLocalDateTime(value));
                case "avatar" -> entity.setAvatar(isUnset ? null : Convert.toStr(value));
                case "introduction" -> entity.setIntroduction(isUnset ? null : Convert.toStr(value));
                case "mfaSecret" -> entity.setMfaSecret(isUnset ? null : Convert.toStr(value));
                case "password" -> entity.setPassword(isUnset ? null : Convert.toStr(value));
                case "showName" -> entity.setShowName(isUnset ? null : Convert.toStr(value));
                case "slogan" -> entity.setSlogan(isUnset ? null : Convert.toStr(value));
                case "username" -> entity.setUsername(isUnset ? null : Convert.toStr(value));
                case "roles" -> entity.setRoles(isUnset ? null : Convert.toList(String.class, value));
                default -> log.warn("Unknown field: {} with value: {}", logicalFieldName, value);
            }
        } catch (Exception e) {
            log.error("Error setting field {} with value {}: {}", logicalFieldName, value, e.getMessage());
        }
    }

}

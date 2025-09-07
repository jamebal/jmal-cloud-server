package com.jmal.clouddisk.dao.impl.jpa;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IAccessTokenDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.AccessTokenRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.accesstoken.AccessTokenOperation;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.exception.ExceptionType;
import com.jmal.clouddisk.model.UserAccessTokenDO;
import com.jmal.clouddisk.model.UserAccessTokenDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.util.TimeUntils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class AccessTokenDAOJpaImpl implements IAccessTokenDAO, IWriteCommon<UserAccessTokenDO> {

    private final AccessTokenRepository accessTokenRepository;

    private final IWriteService writeService;

    @Override
    public UserAccessTokenDO getUserNameByAccessToken(String accessToken) {
        try {
            UserAccessTokenDO result = accessTokenRepository.findByAccessToken(accessToken).orElse(null);
            log.debug("JPA查询AccessToken: {}, 结果: {}", accessToken, result != null ? "找到" : "未找到");
            return result;
        } catch (Exception e) {
            log.error("JPA查询AccessToken失败: accessToken={}, error={}", accessToken, e.getMessage(), e);
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "数据库查询失败");
        }
    }

    @Override
    public void generateAccessToken(UserAccessTokenDO userAccessTokenDO) {
        validateUserAccessToken(userAccessTokenDO);

        try {
            // 检查名称是否已存在
            if (accessTokenRepository.existsByName(userAccessTokenDO.getName())) {
                throw new CommonException(ExceptionType.EXISTING_RESOURCES.getCode(), "该名称已存在");
            }
            // 设置创建时间并保存
            userAccessTokenDO.setCreateTime(LocalDateTime.now(TimeUntils.ZONE_ID));
            CompletableFuture<Void> future = writeService.submit(new AccessTokenOperation.Create(userAccessTokenDO));
            future.get(10, TimeUnit.SECONDS);
        } catch (CommonException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("JPA创建AccessToken失败: name={}, error={}",
                    userAccessTokenDO.getName(), e.getMessage(), e);
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "数据库操作失败");
        }
    }

    @Override
    public void deleteAllByUser(List<ConsumerDO> userList) {
        if (userList == null || userList.isEmpty()) {
            log.debug("用户列表为空，无需删除Token");
            return;
        }

        try {
            List<String> usernames = userList.stream()
                    .map(ConsumerDO::getUsername)
                    .collect(Collectors.toList());
            writeService.submit(new AccessTokenOperation.DeleteByUsernameIn(usernames));
        } catch (Exception e) {
            log.error("JPA删除用户Token失败: userCount={}, error={}",
                    userList.size(), e.getMessage(), e);
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "数据库操作失败");
        }
    }

    @Override
    public List<UserAccessTokenDTO> accessTokenList(String username) {
        try {
            List<UserAccessTokenDO> tokenList = accessTokenRepository.findByUsername(username);

            List<UserAccessTokenDTO> result = tokenList.stream().map(token -> {
                UserAccessTokenDTO dto = new UserAccessTokenDTO();
                BeanUtils.copyProperties(token, dto);
                return dto;
            }).collect(Collectors.toList());

            log.debug("JPA查询AccessToken列表: username={}, count={}", username, result.size());
            return result;
        } catch (Exception e) {
            log.error("JPA查询AccessToken列表失败: username={}, error={}", username, e.getMessage(), e);
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "数据库查询失败");
        }
    }

    @Override
    public void updateAccessToken(String username, String token) {
        try {
            writeService.submit(new AccessTokenOperation.UpdateLastActiveTimeByUsernameAndToken(username, token, LocalDateTime.now(TimeUntils.ZONE_ID)));

        } catch (Exception e) {
            log.error("JPA更新AccessToken失败: username={}, error={}", username, e.getMessage(), e);
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "数据库操作失败");
        }
    }

    @Override
    public void deleteAccessToken(String id) {
        try {
            if (accessTokenRepository.existsById(id)) {
                CompletableFuture<Void> future = writeService.submit(new AccessTokenOperation.DeleteById(id));
                future.get(10, TimeUnit.SECONDS);
            } else {
                log.warn("JPA删除AccessToken失败: 令牌不存在, id={}", id);
                throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "访问令牌不存在");
            }
        } catch (CommonException e) {
            throw e;
        } catch (Exception e) {
            log.error("JPA删除AccessToken失败: id={}, error={}", id, e.getMessage(), e);
            throw new CommonException(ExceptionType.SYSTEM_ERROR.getCode(), "数据库操作失败");
        }
    }

    private void validateUserAccessToken(UserAccessTokenDO userAccessTokenDO) {
        if (CharSequenceUtil.isBlank(userAccessTokenDO.getName())) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), "令牌名称不能为空");
        }
        if (CharSequenceUtil.isBlank(userAccessTokenDO.getUsername())) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), "用户名不能为空");
        }
        if (CharSequenceUtil.isBlank(userAccessTokenDO.getAccessToken())) {
            throw new CommonException(ExceptionType.MISSING_PARAMETERS.getCode(), "访问令牌不能为空");
        }
    }

    @Override
    public void AsyncSaveAll(Iterable<UserAccessTokenDO> entities) {
        writeService.submit(new AccessTokenOperation.CreateAll(entities));
    }
}

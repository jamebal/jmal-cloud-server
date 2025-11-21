package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.dao.util.MyQuery;
import com.jmal.clouddisk.dao.util.MyUpdate;
import com.jmal.clouddisk.model.query.QueryUserDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import org.springframework.data.domain.Page;

import java.util.Collection;
import java.util.List;

public interface IUserDAO {

    ConsumerDO save(ConsumerDO consumerDO);

    void saveAll(Collection<ConsumerDO> consumerDOCollection);

    List<ConsumerDO> findAllById(List<String> idList);

    void deleteAllById(List<String> idList);

    ConsumerDO findById(String userId);

    void upsert(MyQuery myQuery, MyUpdate myUpdate);

    ConsumerDO findByUsername(String username);

    ConsumerDO findOneByCreatorTrue();

    long count();

    Page<ConsumerDO> findUserList(QueryUserDTO queryDTO);

    ConsumerDO findByShowName(String showName);

    String getUsernameById(String userId);

    List<String> findUsernamesByRoleIdList(Collection<String> roleIds);

    boolean resetAdminPassword(String hash);

    void resetMfaForAllUsers();

    /**
     * 根据用户组ID查询包含该组的用户列表
     * @param groupIdList 用户组ID列表
     * @return 用户列表
     */
    List<String> findUsernamesByGroupIdList(Collection<String> groupIdList);

    List<ConsumerDO> findAllByUsername(List<String> toRemoveUsernameList);
}

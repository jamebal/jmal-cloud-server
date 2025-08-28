package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.dao.util.MyQuery;
import com.jmal.clouddisk.dao.util.MyUpdate;
import com.jmal.clouddisk.model.query.QueryUserDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import org.springframework.data.domain.Page;

import java.util.List;

public interface IUserDAO {

    ConsumerDO save(ConsumerDO consumerDO);

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
}

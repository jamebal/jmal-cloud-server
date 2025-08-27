package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.dao.util.MyQuery;
import com.jmal.clouddisk.dao.util.MyUpdate;
import com.jmal.clouddisk.model.rbac.ConsumerDO;

import java.util.List;

public interface IUserDAO {

    ConsumerDO save(ConsumerDO consumerDO);

    List<ConsumerDO> findAllById(List<String> idList);

    void deleteAllById(List<String> idList);

    ConsumerDO findById(String userId);

    void upsert(MyQuery myQuery, MyUpdate myUpdate);

    ConsumerDO findByUsername(String username);

    ConsumerDO findOneByCreatorTrue();
}

package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.oss.web.model.OssConfigDO;

import java.util.List;

public interface IOssConfigDAO {

    List<OssConfigDO> findAll();

    List<OssConfigDO> findAllByUserId(String userId);

    OssConfigDO findById(String id);

    void updateOssConfigBy(OssConfigDO ossConfigDO);

    OssConfigDO findAndRemoveById(String id);
}

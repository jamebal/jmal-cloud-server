package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.dao.impl.jpa.IWriteCommon;
import com.jmal.clouddisk.office.model.OfficeConfigDO;

public interface IOfficeConfigDAO extends IWriteCommon<OfficeConfigDO> {

    void upsert(OfficeConfigDO officeConfigDO);

    OfficeConfigDO findOne();
}

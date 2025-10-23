package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.office.model.OfficeConfigDO;

public interface IOfficeConfigDAO {

    void upsert(OfficeConfigDO officeConfigDO);

    OfficeConfigDO findOne();
}

package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.LdapConfigDO;

public interface ILdapConfigDAO {

    LdapConfigDO findOne();

    void save(LdapConfigDO ldapConfigDO);
}

package com.jmal.clouddisk.dao.impl.jpa.write.ldapconfig;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface ILdapConfigOperation<R> extends IDataOperation<R>
        permits LdapConfigOperation.CreateAll {

}

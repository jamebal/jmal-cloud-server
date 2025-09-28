package com.jmal.clouddisk.dao.write.ldapconfig;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface ILdapConfigOperation<R> extends IDataOperation<R>
        permits LdapConfigOperation.CreateAll {

}

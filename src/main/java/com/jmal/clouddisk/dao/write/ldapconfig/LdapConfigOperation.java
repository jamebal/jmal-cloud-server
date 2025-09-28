package com.jmal.clouddisk.dao.write.ldapconfig;

import com.jmal.clouddisk.model.LdapConfigDO;

public final class LdapConfigOperation {
    private LdapConfigOperation() {}

    public record CreateAll(Iterable<LdapConfigDO> entities) implements ILdapConfigOperation<Void> {}
}

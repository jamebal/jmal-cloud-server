package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.ILdapConfigDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.LdapConfigRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.ldapconfig.LdapConfigOperation;
import com.jmal.clouddisk.model.LdapConfigDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class LdapConfigDAOJpaImpl implements ILdapConfigDAO, IWriteCommon<LdapConfigDO> {

    private final LdapConfigRepository ldapConfigRepository;

    private final IWriteService writeService;


    @Override
    public void AsyncSaveAll(Iterable<LdapConfigDO> entities) {
        writeService.submit(new LdapConfigOperation.CreateAll(entities));
    }

    @Override
    public LdapConfigDO findOne() {
        return ldapConfigRepository.findAll().stream().findFirst().orElse(null);
    }

    @Override
    public void save(LdapConfigDO ldapConfigDO) {
        writeService.submit( new LdapConfigOperation.CreateAll(java.util.List.of(ldapConfigDO)) );
    }
}

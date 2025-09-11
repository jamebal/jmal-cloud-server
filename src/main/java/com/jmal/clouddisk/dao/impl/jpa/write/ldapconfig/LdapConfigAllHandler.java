package com.jmal.clouddisk.dao.impl.jpa.write.ldapconfig;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.LdapConfigRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("logLdapConfigAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class LdapConfigAllHandler implements IDataOperationHandler<LdapConfigOperation.CreateAll, Void> {

    private final LdapConfigRepository repo;

    @Override
    public Void handle(LdapConfigOperation.CreateAll op) {
        repo.saveAll(op.entities());
        return null;
    }
}

package com.jmal.clouddisk.dao.write.ldapconfig;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.LdapConfigRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("ldapConfigAllHandler")
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

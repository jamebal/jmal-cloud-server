package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.LdapConfigDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface LdapConfigRepository extends JpaRepository<LdapConfigDO, String>, JpaSpecificationExecutor<LdapConfigDO> {

}

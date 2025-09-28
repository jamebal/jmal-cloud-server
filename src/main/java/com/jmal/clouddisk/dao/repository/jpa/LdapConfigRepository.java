package com.jmal.clouddisk.dao.repository.jpa;

import com.jmal.clouddisk.model.LdapConfigDO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LdapConfigRepository extends JpaRepository<LdapConfigDO, String>, JpaSpecificationExecutor<LdapConfigDO> {

}

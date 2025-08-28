package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.file.FileEntityDO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface FileJpaRepository extends JpaRepository<FileEntityDO, String>, JpaSpecificationExecutor<ConsumerDO> {

}

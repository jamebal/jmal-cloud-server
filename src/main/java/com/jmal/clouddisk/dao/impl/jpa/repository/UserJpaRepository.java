package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface UserJpaRepository extends JpaRepository<ConsumerDO, String>, JpaSpecificationExecutor<ConsumerDO> {


    Optional<ConsumerDO> findByUsername(String username);

    Optional<ConsumerDO> findOneByCreatorTrue();

}

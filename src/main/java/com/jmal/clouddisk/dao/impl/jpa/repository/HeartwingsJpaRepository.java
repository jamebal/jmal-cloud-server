package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.HeartwingsDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
@JpaRepositoryMarker
@Conditional(RelationalDataSourceCondition.class)
public interface HeartwingsJpaRepository extends JpaRepository<HeartwingsDO, String> {

    long count();
}

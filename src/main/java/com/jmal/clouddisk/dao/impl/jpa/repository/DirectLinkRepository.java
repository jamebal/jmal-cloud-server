package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.DirectLink;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface DirectLinkRepository extends JpaRepository<DirectLink, String>, JpaSpecificationExecutor<DirectLink> {

    DirectLink findByMark(String mark);

    DirectLink findByFileId(String fileId);

    boolean existsByMark(String mark);

    void deleteByUserId(String userId);

}

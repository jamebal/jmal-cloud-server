package com.jmal.clouddisk.dao.repository.jpa;

import com.jmal.clouddisk.model.DirectLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DirectLinkRepository extends JpaRepository<DirectLink, String>, JpaSpecificationExecutor<DirectLink> {

    DirectLink findByMark(String mark);

    DirectLink findByFileId(String fileId);

    boolean existsByMark(String mark);

    void deleteByUserId(String userId);

}

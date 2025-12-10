package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.oss.web.model.OssConfigDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface OssConfigRepository extends JpaRepository<OssConfigDO, String> {

    List<OssConfigDO> findAllByUserId(String userId);
}

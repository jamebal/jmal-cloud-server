package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.oss.web.model.OssConfigDO;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface OssConfigRepository extends JpaRepository<OssConfigDO, String> {

    Optional<OssConfigDO> findById(@NotNull String id);

    List<OssConfigDO> findAllByUserId(String userId);
}

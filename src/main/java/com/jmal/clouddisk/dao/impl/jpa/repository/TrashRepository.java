package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.file.TrashEntityDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface TrashRepository extends JpaRepository<TrashEntityDO, String> {

    /**
     * 计算用户在回收站中的文件总大小
     *
     * @param userId 用户ID
     * @return 总大小（字节）
     */
    @Query("SELECT COALESCE(SUM(t.size), 0) FROM TrashEntityDO t WHERE t.userId = :userId AND t.isFolder = false")
    Long calculateTotalSizeByUserId(@Param("userId") String userId);
}

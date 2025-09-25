package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.file.TrashEntityDO;
import com.jmal.clouddisk.model.file.dto.FileBaseDTO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface TrashRepository extends JpaRepository<TrashEntityDO, Long> {

    /**
     * 计算用户在回收站中的文件总大小
     *
     * @param userId 用户ID
     * @return 总大小（字节）
     */
    @Query("SELECT COALESCE(SUM(t.size), 0) FROM TrashEntityDO t WHERE t.userId = :userId AND t.isFolder = false")
    Long calculateTotalSizeByUserId(@Param("userId") String userId);

    @Query("SELECT t.publicId FROM TrashEntityDO t")
    List<String> findAllIds();

    Page<TrashEntityDO> findAllByHiddenIsFalseAndUserId(String userId, Pageable pageable);

    void deleteByPublicId(String publicId);

    Optional<TrashEntityDO> findByPublicId(String publicId);

    void deleteAllByPublicIdIn(Collection<String> publicIds);

    @Query("SELECT new com.jmal.clouddisk.model.file.dto.FileBaseDTO(t.publicId, t.name, t.path, t.userId, t.isFolder) FROM TrashEntityDO t WHERE t.publicId IN :ids")
    List<FileBaseDTO> findAllTrashFileBaseDTOByIdIn(List<String> ids);
}

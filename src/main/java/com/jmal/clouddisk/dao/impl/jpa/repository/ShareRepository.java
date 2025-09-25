package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.ShareDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface ShareRepository extends JpaRepository<ShareDO, String> {

    // 更新需要设置提取码的分享
    @Modifying
    @Query("UPDATE ShareDO p SET p.fatherShareId = :fatherShareId, p.isPrivacy = :isPrivacy, p.extractionCode = :extractionCode " +
            "WHERE p.fileId IN :fileIds")
    void updateSubShare(
            @Param("fileIds") List<String> fileIds,
            @Param("isPrivacy") Boolean isPrivacy,
            @Param("fatherShareId") String fatherShareId,
            @Param("extractionCode") String extractionCode
    );

    @Modifying
    @Query("UPDATE ShareDO p SET p.fileName = :newFileName WHERE p.fileId = :fileId")
    void SetFileNameByFileId(String fileId, String newFileName);

    boolean existsByShortId(String shortId);

    Optional<ShareDO> findByFileId(String fileId);

    ShareDO findByFatherShareId(String fatherShareId);

    boolean existsByFatherShareIdIn(Collection<String> fatherShareIds);

    Optional<ShareDO> findByShortId(String shortId);

    @Query(value = "SELECT s FROM ShareDO s " +
            "WHERE s.userId = :userId ",
            countQuery = "SELECT COUNT(s) FROM ShareDO s " +
                    "WHERE s.userId = :userId ")
    Page<ShareDO> findShareList(@Param("userId") String userId, Pageable pageable);

    void removeByFileIdIn(Collection<String> fileIds);

    void removeByFatherShareId(String fatherShareId);

    void removeByUserId(String userId);

    @Query("SELECT s FROM ShareDO s WHERE s.fileId LIKE :pathPrefix ESCAPE '\\'")
    List<ShareDO> findByFileIdStartingWith(String pathPrefix);
}

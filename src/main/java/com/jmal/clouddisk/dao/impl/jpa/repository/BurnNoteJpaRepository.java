package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.BurnNoteDO;
import com.jmal.clouddisk.model.dto.BurnNoteVO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface BurnNoteJpaRepository extends JpaRepository<BurnNoteDO, String> {


    @Modifying
    @Query("DELETE FROM BurnNoteDO b WHERE b.id in :ids")
    int deleteExpiredNotes(Collection<String> ids);

    @Query("SELECT b.id FROM BurnNoteDO b WHERE (b.expireAt IS NOT NULL AND b.expireAt < :now) " +
            "OR (b.createdTime < :expireAt)")
    Collection<String> findExpiredNotes(Instant now, Instant expireAt);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM BurnNoteDO b")
    boolean existsAny();

    @Query("SELECT new com.jmal.clouddisk.model.dto.BurnNoteVO(b.id, b.userId, b.isFile, b.fileSize, b.viewsLeft, b.expireAt, b.createdTime) FROM BurnNoteDO b")
    List<BurnNoteVO> findAllBurnNoteVO(Pageable pageable);

    @Query("SELECT new com.jmal.clouddisk.model.dto.BurnNoteVO(b.id, b.userId, b.isFile, b.fileSize, b.viewsLeft, b.expireAt, b.createdTime) FROM BurnNoteDO b WHERE b.userId = :userId")
    List<BurnNoteVO> findAllBurnNoteVOByUserId(String userId, Pageable pageable);
}

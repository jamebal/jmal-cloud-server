package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.BurnNoteDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface BurnNoteJpaRepository extends JpaRepository<BurnNoteDO, String> {


    @Modifying
    @Query("DELETE FROM BurnNoteDO b WHERE b.id in :ids")
    int deleteExpiredNotes(Collection<String> ids);

    @Query("SELECT b.id FROM BurnNoteDO b WHERE b.expireAt IS NOT NULL AND b.expireAt < :now " +
            "OR b.createdTime < :expireAt")
    Collection<String> findExpiredNotes(LocalDateTime now, LocalDateTime expireAt);

    @Query("SELECT CASE WHEN COUNT(b) > 0 THEN true ELSE false END FROM BurnNoteDO b")
    boolean existsAny();
}

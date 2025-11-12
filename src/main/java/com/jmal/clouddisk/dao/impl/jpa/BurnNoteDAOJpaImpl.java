package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.BurnNoteFileService;
import com.jmal.clouddisk.dao.IBurnNoteDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.BurnNoteJpaRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.burnnote.BurnNoteOperation;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.BurnNoteDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class BurnNoteDAOJpaImpl implements IBurnNoteDAO {

    private final IWriteService writeService;

    private final BurnNoteJpaRepository burnNoteJpaRepository;

    private final BurnNoteFileService burnNoteFileService;

    @Override
    public BurnNoteDO save(BurnNoteDO burnNoteDO) {
        try {
            return writeService.submit(new BurnNoteOperation.Create(burnNoteDO)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("JPA保存阅后即焚笔记失败: {}, error={}", burnNoteDO, e.getMessage(), e);
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public BurnNoteDO findById(String id) {
        return burnNoteJpaRepository.findById(id).orElse(null);
    }

    @Override
    public void deleteById(String id) {
        try {
            writeService.submit(new BurnNoteOperation.Delete(id)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("JPA删除阅后即焚笔记失败: id={}, error={}", id, e.getMessage(), e);
            throw new CommonException(e.getMessage());
        }
    }

    private Collection<String> findExpiredNotes() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireAt = now.minusHours(24);
        return burnNoteJpaRepository.findExpiredNotes(now, expireAt);
    }

    @Override
    public long deleteExpiredNotes() {
        try {
            Collection<String> expiredNoteIds = findExpiredNotes();
            if (expiredNoteIds.isEmpty()) {
                return 0L;
            }
            expiredNoteIds.forEach(burnNoteFileService::deleteAllChunks);
            return writeService.submit(new BurnNoteOperation.DeleteAllByIds(expiredNoteIds)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("JPA清理过期阅后即焚笔记失败: error={}", e.getMessage(), e);
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public boolean existData() {
        return burnNoteJpaRepository.existsAny();
    }
}

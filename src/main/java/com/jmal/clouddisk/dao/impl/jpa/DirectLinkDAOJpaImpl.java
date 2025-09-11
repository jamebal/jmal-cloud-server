package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IDirectLinkDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.DirectLinkRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.directlink.DirectLinkOperation;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.DirectLink;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DirectLinkDAOJpaImpl implements IDirectLinkDAO, IWriteCommon<DirectLink> {

    private final DirectLinkRepository directLinkRepository;

    private final IWriteService writeService;


    @Override
    public void AsyncSaveAll(Iterable<DirectLink> entities) {
        writeService.submit(new DirectLinkOperation.CreateAll(entities));
    }

    @Override
    public void removeByUserId(String userId) {
        try {
            writeService.submit(new DirectLinkOperation.DeleteByUserId(userId)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public DirectLink findByMark(String mark) {
        return directLinkRepository.findByMark(mark);
    }

    @Override
    public void updateByFileId(String fileId, String mark, String userId, LocalDateTime now) {
        try {
            writeService.submit(new DirectLinkOperation.UpdateByFileId(fileId, mark, userId, now)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public boolean existsByMark(String mark) {
        return directLinkRepository.existsByMark(mark);
    }

    @Override
    public DirectLink findByFileId(String fileId) {
        return directLinkRepository.findByFileId(fileId);
    }
}

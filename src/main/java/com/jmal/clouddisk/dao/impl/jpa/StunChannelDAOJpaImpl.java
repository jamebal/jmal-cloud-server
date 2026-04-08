package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.IStunChannelDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.StunChannelRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.stunchannel.StunChannelOperation;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.stun.StunChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class StunChannelDAOJpaImpl implements IStunChannelDAO, IWriteCommon<StunChannel> {

    private final StunChannelRepository stunChannelRepository;

    private final IWriteService writeService;

    @Override
    public void AsyncSaveAll(Iterable<StunChannel> entities) {
        try {
            writeService.submit(new StunChannelOperation.CreateAll(entities)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void upsert(String channelId, String addr) {
        try {
            writeService.submit(new StunChannelOperation.Upsert(channelId, addr)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public Optional<StunChannel> findByChannelId(String channelId) {
        return stunChannelRepository.findByChannelId(channelId);
    }
}

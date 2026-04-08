package com.jmal.clouddisk.dao.impl.mongodb;

import com.jmal.clouddisk.dao.IStunChannelDAO;
import com.jmal.clouddisk.model.stun.StunChannel;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class StunChannelDAOImpl implements IStunChannelDAO {

    private static final String CHANNEL_ID = "channelId";

    private static final String ADDR = "addr";

    private final MongoTemplate mongoTemplate;

    @Override
    public void upsert(String channelId, String addr) {
        Query query = new Query(Criteria.where(CHANNEL_ID).is(channelId));
        Update update = new Update();
        update.set(CHANNEL_ID, channelId);
        update.set(ADDR, addr);
        mongoTemplate.upsert(query, update, StunChannel.class);
    }

    @Override
    public Optional<StunChannel> findByChannelId(String channelId) {
        Query query = new Query(Criteria.where(CHANNEL_ID).is(channelId));
        return Optional.ofNullable(mongoTemplate.findOne(query, StunChannel.class));
    }
}

package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.stun.StunChannel;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface StunChannelRepository extends JpaRepository<StunChannel, String> {

    Optional<StunChannel> findByChannelId(String channelId);
}

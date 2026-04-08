package com.jmal.clouddisk.model.stun;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import com.jmal.clouddisk.service.impl.StunChannelService;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@Setter
@Document(collection = StunChannelService.COLLECTION_NAME)
@CompoundIndexes({
        @CompoundIndex(name = "channel_id_1", def = "{'channelId': 1}", unique = true)
})
@Entity
@Table(name = StunChannelService.COLLECTION_NAME,
        indexes = {
                @Index(name = "stun_channels_channel_id", columnList = "channelId", unique = true)
        }
)
public class StunChannel extends AuditableEntity implements Reflective {

    @Column(nullable = false, unique = true, length = 128)
    private String channelId;

    @Column(nullable = false, length = 255)
    private String addr;
}

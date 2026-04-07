package com.jmal.clouddisk.stun;

import com.jmal.clouddisk.dao.IStunChannelDAO;
import com.jmal.clouddisk.model.stun.StunChannel;
import com.jmal.clouddisk.model.stun.StunGostNode;
import com.jmal.clouddisk.model.stun.StunGostNodesQuery;
import com.jmal.clouddisk.service.impl.StunChannelService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StunChannelServiceTest {

    @Test
    void updateSuccessThenRead() {
        InMemoryStunChannelDAO dao = new InMemoryStunChannelDAO();
        StunChannelService service = new StunChannelService(dao);

        service.updateChannelAddress("home", "1.2.3.4:5678");

        assertThat(service.getChannelAddress("home")).contains("1.2.3.4:5678");
    }

    @Test
    void sameChannelUpdatesOverwriteOldAddress() {
        InMemoryStunChannelDAO dao = new InMemoryStunChannelDAO();
        StunChannelService service = new StunChannelService(dao);

        service.updateChannelAddress("home", "1.2.3.4:5678");
        service.updateChannelAddress("home", "5.6.7.8:9876");

        assertThat(service.getChannelAddress("home")).contains("5.6.7.8:9876");
    }

    @Test
    void differentChannelsDoNotAffectEachOther() {
        InMemoryStunChannelDAO dao = new InMemoryStunChannelDAO();
        StunChannelService service = new StunChannelService(dao);

        service.updateChannelAddress("home-a", "1.2.3.4:5678");
        service.updateChannelAddress("home-b", "5.6.7.8:9876");

        assertThat(service.getChannelAddress("home-a")).contains("1.2.3.4:5678");
        assertThat(service.getChannelAddress("home-b")).contains("5.6.7.8:9876");
    }

    @Test
    void blankAddressIsRejected() {
        InMemoryStunChannelDAO dao = new InMemoryStunChannelDAO();
        StunChannelService service = new StunChannelService(dao);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.updateChannelAddress("home", " "));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void defaultGostNodesUseSocks5TlsAndSecureTrue() {
        InMemoryStunChannelDAO dao = new InMemoryStunChannelDAO();
        StunChannelService service = new StunChannelService(dao);
        service.updateChannelAddress("home", "1.2.3.4:5678");

        List<StunGostNode> nodes = service.getGostNodes("home", new StunGostNodesQuery());

        assertThat(nodes).hasSize(1);
        StunGostNode node = nodes.getFirst();
        assertThat(node.getName()).isEqualTo("home");
        assertThat(node.getAddr()).isEqualTo("1.2.3.4:5678");
        assertThat(node.getConnector().getType()).isEqualTo("socks5");
        assertThat(node.getConnector().getAuth()).isNull();
        assertThat(node.getDialer().getType()).isEqualTo("tls");
        assertThat(node.getDialer().getTls()).isNotNull();
        assertThat(node.getDialer().getTls().getSecure()).isTrue();
    }

    @Test
    void explicitConnectorAndDialerOverridesStayCompatible() {
        InMemoryStunChannelDAO dao = new InMemoryStunChannelDAO();
        StunChannelService service = new StunChannelService(dao);
        service.updateChannelAddress("home", "1.2.3.4:5678");

        StunGostNodesQuery query = new StunGostNodesQuery();
        query.setConnector("ss");
        query.setDialer("tcp");
        query.setUsername("username");
        query.setPassword("pwd");

        List<StunGostNode> nodes = service.getGostNodes("home", query);

        assertThat(nodes).hasSize(1);
        StunGostNode node = nodes.getFirst();
        assertThat(node.getConnector().getType()).isEqualTo("ss");
        assertThat(node.getConnector().getAuth()).isNotNull();
        assertThat(node.getConnector().getAuth().getUsername()).isEqualTo("username");
        assertThat(node.getConnector().getAuth().getPassword()).isEqualTo("pwd");
        assertThat(node.getDialer().getType()).isEqualTo("tcp");
        assertThat(node.getDialer().getTls()).isNull();
    }

    @Test
    void usernameAndPasswordMustAppearTogether() {
        InMemoryStunChannelDAO dao = new InMemoryStunChannelDAO();
        StunChannelService service = new StunChannelService(dao);
        service.updateChannelAddress("home", "1.2.3.4:5678");

        StunGostNodesQuery query = new StunGostNodesQuery();
        query.setUsername("demo-user");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.getGostNodes("home", query));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void restartRestoresPersistedData() {
        InMemoryStunChannelDAO dao = new InMemoryStunChannelDAO();
        StunChannelService firstService = new StunChannelService(dao);
        firstService.updateChannelAddress("home", "1.2.3.4:5678");

        StunChannelService restartedService = new StunChannelService(dao);

        assertThat(restartedService.getChannelAddress("home")).contains("1.2.3.4:5678");
    }

    @Test
    void persistenceFailureReturnsErrorWithoutPollutingCurrentState() {
        InMemoryStunChannelDAO dao = new InMemoryStunChannelDAO();
        dao.upsert("home", "1.2.3.4:5678");
        dao.failOnUpsert = true;
        StunChannelService service = new StunChannelService(dao);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.updateChannelAddress("home", "5.6.7.8:9876"));

        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(service.getChannelAddress("home")).contains("1.2.3.4:5678");
    }

    private static final class InMemoryStunChannelDAO implements IStunChannelDAO {

        private final Map<String, StunChannel> storage = new HashMap<>();

        private boolean failOnUpsert;

        @Override
        public void upsert(String channelId, String addr) {
            if (failOnUpsert) {
                throw new RuntimeException("persistence failed");
            }
            StunChannel channel = storage.computeIfAbsent(channelId, key -> new StunChannel());
            channel.setChannelId(channelId);
            channel.setAddr(addr);
        }

        @Override
        public Optional<StunChannel> findByChannelId(String channelId) {
            return Optional.ofNullable(storage.get(channelId));
        }
    }
}

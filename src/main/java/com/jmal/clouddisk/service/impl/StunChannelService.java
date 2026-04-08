package com.jmal.clouddisk.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.IStunChannelDAO;
import com.jmal.clouddisk.model.stun.StunChannel;
import com.jmal.clouddisk.model.stun.StunGostNode;
import com.jmal.clouddisk.model.stun.StunGostNodesQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class StunChannelService {

    public static final String COLLECTION_NAME = "stun_channels";

    private static final String DEFAULT_CONNECTOR = "socks5";

    private static final String DEFAULT_DIALER = "tls";

    private final IStunChannelDAO stunChannelDAO;

    public void updateChannelAddress(String channelId, String addr) {
        String normalizedChannelId = requireChannelId(channelId);
        String normalizedAddr = normalizeAddr(addr);
        validateHostPort(normalizedAddr);
        try {
            stunChannelDAO.upsert(normalizedChannelId, normalizedAddr);
        } catch (RuntimeException e) {
            log.error("Failed to persist stun channel address, channelId={}", normalizedChannelId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to persist channel address", e);
        }
    }

    public Optional<String> getChannelAddress(String channelId) {
        return stunChannelDAO.findByChannelId(requireChannelId(channelId)).map(StunChannel::getAddr);
    }

    public List<StunGostNode> getGostNodes(String channelId, StunGostNodesQuery query) {
        String normalizedChannelId = requireChannelId(channelId);
        String addr = stunChannelDAO.findByChannelId(normalizedChannelId)
                .map(StunChannel::getAddr)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));

        StunGostNodesQuery normalizedQuery = query == null ? new StunGostNodesQuery() : query;
        String username = normalizeOptional(normalizedQuery.getUsername());
        String password = normalizeOptional(normalizedQuery.getPassword());
        if ((username == null) != (password == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "username and password must be provided together");
        }

        String connectorType = defaultIfBlank(normalizedQuery.getConnector(), DEFAULT_CONNECTOR);
        String dialerType = defaultIfBlank(normalizedQuery.getDialer(), DEFAULT_DIALER);
        String nodeName = defaultIfBlank(normalizedQuery.getName(), normalizedChannelId);

        StunGostNode node = new StunGostNode();
        node.setName(nodeName);
        node.setAddr(addr);

        StunGostNode.Connector connector = new StunGostNode.Connector();
        connector.setType(connectorType);
        if (username != null) {
            StunGostNode.Auth auth = new StunGostNode.Auth();
            auth.setUsername(username);
            auth.setPassword(password);
            connector.setAuth(auth);
        }
        node.setConnector(connector);

        StunGostNode.Dialer dialer = new StunGostNode.Dialer();
        dialer.setType(dialerType);
        if ("tls".equalsIgnoreCase(dialerType)) {
            StunGostNode.Dialer.Tls tls = new StunGostNode.Dialer.Tls();
            tls.setSecure(parseSecure(normalizedQuery.getSecure()));
            tls.setServerName(normalizeOptional(normalizedQuery.getServerName()));
            tls.setCaFile(normalizeOptional(normalizedQuery.getCaFile()));
            dialer.setTls(tls);
        }
        node.setDialer(dialer);

        return List.of(node);
    }

    private static String requireChannelId(String channelId) {
        String normalized = normalizeOptional(channelId);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channelId cannot be blank");
        }
        return normalized;
    }

    private static String normalizeAddr(String addr) {
        String normalized = normalizeOptional(addr);
        if (normalized == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "addr cannot be blank");
        }
        return normalized;
    }

    private static void validateHostPort(String addr) {
        try {
            URI uri = new URI("http://" + addr);
            if (CharSequenceUtil.isBlank(uri.getHost()) || uri.getPort() < 1 || uri.getPort() > 65535) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "addr must be a valid host:port");
            }
            if (CharSequenceUtil.isNotBlank(uri.getPath()) && !"/".equals(uri.getPath())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "addr must be a valid host:port");
            }
            if (uri.getQuery() != null || uri.getFragment() != null || uri.getUserInfo() != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "addr must be a valid host:port");
            }
        } catch (IllegalArgumentException | java.net.URISyntaxException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "addr must be a valid host:port", e);
        }
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        String normalized = normalizeOptional(value);
        return normalized == null ? defaultValue : normalized;
    }

    private static String normalizeOptional(String value) {
        if (CharSequenceUtil.isBlank(value)) {
            return null;
        }
        return value.trim();
    }

    private static boolean parseSecure(String secure) {
        String normalized = normalizeOptional(secure);
        if (normalized == null) {
            return true;
        }
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "secure must be true or false");
    }
}

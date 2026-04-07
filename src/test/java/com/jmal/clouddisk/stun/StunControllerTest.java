package com.jmal.clouddisk.stun;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jmal.clouddisk.controller.rest.StunController;
import com.jmal.clouddisk.exception.CommonExceptionHandler;
import com.jmal.clouddisk.model.stun.StunGostNode;
import com.jmal.clouddisk.service.impl.StunChannelService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class StunControllerTest {

    @Mock
    private StunChannelService stunChannelService;

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new StunController(stunChannelService))
                .setControllerAdvice(new CommonExceptionHandler())
                .build();
    }

    @Test
    void updateSuccessThenRead() throws Exception {
        when(stunChannelService.getChannelAddress("home")).thenReturn(Optional.of("1.2.3.4:5678"));

        mockMvc.perform(post("/stun/home/update")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "addr": "1.2.3.4:5678"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<String> channelCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> addrCaptor = ArgumentCaptor.forClass(String.class);
        verify(stunChannelService).updateChannelAddress(channelCaptor.capture(), addrCaptor.capture());
        assertThat(channelCaptor.getValue()).isEqualTo("home");
        assertThat(addrCaptor.getValue()).isEqualTo("1.2.3.4:5678");

        mockMvc.perform(get("/stun/home/get"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("1.2.3.4:5678"));
    }

    @Test
    void missingChannelReturns404() throws Exception {
        when(stunChannelService.getChannelAddress("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/stun/missing/get"))
                .andExpect(status().isNotFound());
    }

    @Test
    void addressJsonReturnsWrappedAddress() throws Exception {
        when(stunChannelService.getChannelAddress("home")).thenReturn(Optional.of("1.2.3.4:5678"));

        mockMvc.perform(get("/stun/home/address"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value("1.2.3.4:5678"));
    }

    @Test
    void addressJsonReturnsWrappedErrorWhenMissing() throws Exception {
        when(stunChannelService.getChannelAddress("missing")).thenReturn(Optional.empty());

        mockMvc.perform(get("/stun/missing/address"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(-1))
                .andExpect(jsonPath("$.message").value("动态地址不存在"));
    }

    @Test
    void gostNodesDefaultResponseUsesSocks5TlsSecureTrue() throws Exception {
        StunGostNode node = new StunGostNode();
        node.setName("home");
        node.setAddr("1.2.3.4:5678");
        StunGostNode.Connector connector = new StunGostNode.Connector();
        connector.setType("socks5");
        node.setConnector(connector);
        StunGostNode.Dialer dialer = new StunGostNode.Dialer();
        dialer.setType("tls");
        StunGostNode.Dialer.Tls tls = new StunGostNode.Dialer.Tls();
        tls.setSecure(true);
        dialer.setTls(tls);
        node.setDialer(dialer);
        when(stunChannelService.getGostNodes(eq("home"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(node));

        mockMvc.perform(get("/stun/home/gost/nodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].connector.type").value("socks5"))
                .andExpect(jsonPath("$[0].dialer.type").value("tls"))
                .andExpect(jsonPath("$[0].dialer.tls.secure").value(true));
    }

    @Test
    void explicitSsTcpOverrideReturnsCompatibleNode() throws Exception {
        StunGostNode node = new StunGostNode();
        node.setName("custom");
        node.setAddr("1.2.3.4:5678");
        StunGostNode.Connector connector = new StunGostNode.Connector();
        connector.setType("ss");
        StunGostNode.Auth auth = new StunGostNode.Auth();
        auth.setUsername("chacha20-ietf-poly1305");
        auth.setPassword("pwd");
        connector.setAuth(auth);
        node.setConnector(connector);
        StunGostNode.Dialer dialer = new StunGostNode.Dialer();
        dialer.setType("tcp");
        node.setDialer(dialer);
        when(stunChannelService.getGostNodes(eq("home"), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(node));

        mockMvc.perform(get("/stun/home/gost/nodes")
                        .param("connector", "ss")
                        .param("dialer", "tcp")
                        .param("username", "chacha20-ietf-poly1305")
                        .param("password", "pwd"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].connector.type").value("ss"))
                .andExpect(jsonPath("$[0].connector.auth.username").value("chacha20-ietf-poly1305"))
                .andExpect(jsonPath("$[0].dialer.type").value("tcp"))
                .andExpect(jsonPath("$[0].dialer.tls").doesNotExist());
    }

    @Test
    void incompleteUsernamePasswordReturns400() throws Exception {
        when(stunChannelService.getGostNodes(eq("home"), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "username and password must be provided together"));

        mockMvc.perform(get("/stun/home/gost/nodes")
                        .param("username", "demo-user"))
                .andExpect(status().isBadRequest());
    }
}

package com.jmal.clouddisk.setting;

import com.jmal.clouddisk.controller.rest.WebsiteSettingController;
import com.jmal.clouddisk.exception.CommonExceptionHandler;
import com.jmal.clouddisk.model.DynamicAddressConfig;
import com.jmal.clouddisk.service.impl.DynamicAddressConfigService;
import com.jmal.clouddisk.service.impl.SettingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WebsiteSettingControllerTest {

    @Mock
    private SettingService settingService;

    @Mock
    private DynamicAddressConfigService dynamicAddressConfigService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new WebsiteSettingController(settingService, dynamicAddressConfigService))
                .setControllerAdvice(new CommonExceptionHandler())
                .build();
    }

    @Test
    void getDynamicAddressConfigReturnsWrappedConfig() throws Exception {
        DynamicAddressConfig config = new DynamicAddressConfig();
        config.setEnabled(true);
        config.setDomain("home.example.com");
        when(dynamicAddressConfigService.getDynamicAddressConfig()).thenReturn(config);

        mockMvc.perform(get("/website/setting/dynamic-address"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.domain").value("home.example.com"));
    }

    @Test
    void updateDynamicAddressConfigDelegatesToService() throws Exception {
        mockMvc.perform(put("/website/setting/dynamic-address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "domain": "home.example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        ArgumentCaptor<DynamicAddressConfig> captor = ArgumentCaptor.forClass(DynamicAddressConfig.class);
        verify(dynamicAddressConfigService).updateDynamicAddressConfig(captor.capture());
        assertThat(captor.getValue().getEnabled()).isTrue();
        assertThat(captor.getValue().getDomain()).isEqualTo("home.example.com");
    }
}

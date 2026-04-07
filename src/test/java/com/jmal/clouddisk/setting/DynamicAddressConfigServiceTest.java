package com.jmal.clouddisk.setting;

import com.jmal.clouddisk.dao.IWebsiteSettingDAO;
import com.jmal.clouddisk.model.DynamicAddressConfig;
import com.jmal.clouddisk.model.NetdiskPersonalization;
import com.jmal.clouddisk.model.WebsiteSettingDO;
import com.jmal.clouddisk.service.impl.DynamicAddressConfigService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicAddressConfigServiceTest {

    @Test
    void missingConfigDefaultsToDisabledAndNoDomain() {
        InMemoryWebsiteSettingDAO dao = new InMemoryWebsiteSettingDAO();
        DynamicAddressConfigService service = new DynamicAddressConfigService(dao);

        DynamicAddressConfig config = service.getDynamicAddressConfig();

        assertThat(config.getEnabled()).isFalse();
        assertThat(config.getDomain()).isNull();
    }

    @Test
    void updatePersistsEnabledAndTrimmedDomain() {
        InMemoryWebsiteSettingDAO dao = new InMemoryWebsiteSettingDAO();
        DynamicAddressConfigService service = new DynamicAddressConfigService(dao);
        DynamicAddressConfig request = new DynamicAddressConfig();
        request.setEnabled(true);
        request.setDomain("  home.example.com  ");

        service.updateDynamicAddressConfig(request);

        DynamicAddressConfigService restartedService = new DynamicAddressConfigService(dao);
        DynamicAddressConfig config = restartedService.getDynamicAddressConfig();
        assertThat(config.getEnabled()).isTrue();
        assertThat(config.getDomain()).isEqualTo("home.example.com");
    }

    @Test
    void blankDomainIsNormalizedToNull() {
        InMemoryWebsiteSettingDAO dao = new InMemoryWebsiteSettingDAO();
        DynamicAddressConfigService service = new DynamicAddressConfigService(dao);
        DynamicAddressConfig request = new DynamicAddressConfig();
        request.setEnabled(true);
        request.setDomain("   ");

        service.updateDynamicAddressConfig(request);

        DynamicAddressConfig config = service.getDynamicAddressConfig();
        assertThat(config.getEnabled()).isTrue();
        assertThat(config.getDomain()).isNull();
    }

    private static final class InMemoryWebsiteSettingDAO implements IWebsiteSettingDAO {

        private final WebsiteSettingDO setting = new WebsiteSettingDO();

        @Override
        public WebsiteSettingDO findOne() {
            return setting;
        }

        @Override
        public void updateLogo(String logo) {
        }

        @Override
        public void updateName(String name) {
        }

        @Override
        public void upsert(WebsiteSettingDO websiteSettingDO) {
        }

        @Override
        public WebsiteSettingDO getPreviewConfig() {
            return setting;
        }

        @Override
        public void updatePreviewConfig(String iframe) {
        }

        @Override
        public void setMfaForceEnable(Boolean mfaForceEnable) {
        }

        @Override
        public void updatePersonalization(NetdiskPersonalization personalization) {
        }

        @Override
        public void updateDynamicAddressConfig(DynamicAddressConfig dynamicAddressConfig) {
            setting.setDynamicAddress(dynamicAddressConfig);
        }
    }
}

package com.jmal.clouddisk.service.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.IWebsiteSettingDAO;
import com.jmal.clouddisk.model.DynamicAddressConfig;
import com.jmal.clouddisk.model.WebsiteSettingDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DynamicAddressConfigService {

    private final IWebsiteSettingDAO websiteSettingDAO;

    public DynamicAddressConfig getDynamicAddressConfig() {
        WebsiteSettingDO websiteSetting = websiteSettingDAO.findOne();
        return normalize(websiteSetting == null ? null : websiteSetting.getDynamicAddress());
    }

    public void updateDynamicAddressConfig(DynamicAddressConfig dynamicAddressConfig) {
        websiteSettingDAO.updateDynamicAddressConfig(normalize(dynamicAddressConfig));
    }

    private DynamicAddressConfig normalize(DynamicAddressConfig dynamicAddressConfig) {
        DynamicAddressConfig normalized = new DynamicAddressConfig();
        if (dynamicAddressConfig == null) {
            normalized.setEnabled(false);
            return normalized;
        }
        normalized.setEnabled(Boolean.TRUE.equals(dynamicAddressConfig.getEnabled()));
        if (!CharSequenceUtil.isBlank(dynamicAddressConfig.getDomain())) {
            normalized.setDomain(dynamicAddressConfig.getDomain().trim());
        }
        return normalized;
    }
}

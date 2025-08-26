package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.WebsiteSettingDO;

public interface IWebsiteSettingDAO {

    WebsiteSettingDO findOne();

    void updateLogo(String logo);

    void updateName(String name);

    void upsert(WebsiteSettingDO websiteSettingDO);

    WebsiteSettingDO getPreviewConfig();

    void updatePreviewConfig(String iframe);

    void setMfaForceEnable(Boolean mfaForceEnable);

}

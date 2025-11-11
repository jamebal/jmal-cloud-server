package com.jmal.clouddisk.dao.impl.jpa.write.setting;

import com.jmal.clouddisk.model.HeartwingsDO;
import com.jmal.clouddisk.model.NetdiskPersonalization;
import com.jmal.clouddisk.model.WebsiteSettingDO;

public final class WebSiteSettingOperation {
    private WebSiteSettingOperation() {}

    public record Create(WebsiteSettingDO entity) implements IWebSiteSettingOperation<Void> {}
    public record CreateAll(Iterable<WebsiteSettingDO> entities) implements IWebSiteSettingOperation<Void> {}
    public record UpdateNetdiskLogo(String logo) implements IWebSiteSettingOperation<Integer> {}
    public record UpdateNetdiskName(String name) implements IWebSiteSettingOperation<Integer> {}
    public record UpdateIframe(String iframe) implements IWebSiteSettingOperation<Integer> {}
    public record UpdateMfaForceEnable(Boolean mfaForceEnable) implements IWebSiteSettingOperation<Integer> {}

    public record CreateHeartwings(HeartwingsDO entity) implements IWebSiteSettingOperation<Void> {}

    public record UpdatePersonalization(NetdiskPersonalization personalization) implements IWebSiteSettingOperation<Integer> {}
}

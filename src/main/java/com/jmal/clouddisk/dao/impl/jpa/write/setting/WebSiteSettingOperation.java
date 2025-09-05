package com.jmal.clouddisk.dao.impl.jpa.write.setting;

import com.jmal.clouddisk.model.WebsiteSettingDO;

public final class WebSiteSettingOperation {
    private WebSiteSettingOperation() {}

    public record CreateAll(Iterable<WebsiteSettingDO> entities) implements IWebSiteSettingOperation {}
}

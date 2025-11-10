package com.jmal.clouddisk.dao.impl.jpa.write.setting;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IWebSiteSettingOperation<R> extends IDataOperation<R>
        permits WebSiteSettingOperation.Create, WebSiteSettingOperation.CreateAll, WebSiteSettingOperation.CreateHeartwings, WebSiteSettingOperation.UpdateIframe, WebSiteSettingOperation.UpdateMfaForceEnable, WebSiteSettingOperation.UpdateNetdiskLogo, WebSiteSettingOperation.UpdateNetdiskName, WebSiteSettingOperation.UpdatePersonalization {

}

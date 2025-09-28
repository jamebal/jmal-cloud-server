package com.jmal.clouddisk.dao.write.setting;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface IWebSiteSettingOperation<R> extends IDataOperation<R>
        permits WebSiteSettingOperation.Create, WebSiteSettingOperation.CreateAll, WebSiteSettingOperation.CreateHeartwings, WebSiteSettingOperation.UpdateNetdiskLogo, WebSiteSettingOperation.UpdateNetdiskName, WebSiteSettingOperation.UpdateIframe, WebSiteSettingOperation.UpdateMfaForceEnable {

}

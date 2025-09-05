package com.jmal.clouddisk.dao.impl.jpa.write.setting;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IWebSiteSettingOperation extends IDataOperation
        permits WebSiteSettingOperation.CreateAll {

}

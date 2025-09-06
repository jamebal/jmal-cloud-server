package com.jmal.clouddisk.dao.impl.jpa.write.share;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IShareOperation<R> extends IDataOperation<R>
        permits ShareOperation.CreateAll {

}

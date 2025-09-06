package com.jmal.clouddisk.dao.impl.jpa.write.tag;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface ITagOperation<R> extends IDataOperation<R>
        permits TagOperation.CreateAll {

}

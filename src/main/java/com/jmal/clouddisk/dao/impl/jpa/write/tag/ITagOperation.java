package com.jmal.clouddisk.dao.impl.jpa.write.tag;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface ITagOperation extends IDataOperation
        permits TagOperation.CreateAll {

}

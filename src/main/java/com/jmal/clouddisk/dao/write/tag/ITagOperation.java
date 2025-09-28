package com.jmal.clouddisk.dao.write.tag;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface ITagOperation<R> extends IDataOperation<R>
        permits TagOperation.Create, TagOperation.CreateAll, TagOperation.RemoveByIdIn, TagOperation.UpdateSortById {

}

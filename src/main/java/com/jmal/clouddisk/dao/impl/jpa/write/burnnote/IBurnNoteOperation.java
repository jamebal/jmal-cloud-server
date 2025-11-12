package com.jmal.clouddisk.dao.impl.jpa.write.burnnote;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IBurnNoteOperation<R> extends IDataOperation<R>
        permits BurnNoteOperation.Create, BurnNoteOperation.Delete, BurnNoteOperation.DeleteAllByIds {
}

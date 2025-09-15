package com.jmal.clouddisk.dao.impl.jpa.write.filehistory;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IFileHistoryOperation<R> extends IDataOperation<R>
        permits FileHistoryOperation.Create, FileHistoryOperation.CreateAll, FileHistoryOperation.DeleteByFileIds, FileHistoryOperation.DeleteByIds, FileHistoryOperation.UpdateFileId {

}

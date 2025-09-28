package com.jmal.clouddisk.dao.write.filehistory;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface IFileHistoryOperation<R> extends IDataOperation<R>
        permits FileHistoryOperation.Create, FileHistoryOperation.CreateAll, FileHistoryOperation.DeleteByFileIds, FileHistoryOperation.DeleteByIds, FileHistoryOperation.UpdateFileId {

}

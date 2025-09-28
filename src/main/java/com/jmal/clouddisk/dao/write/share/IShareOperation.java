package com.jmal.clouddisk.dao.write.share;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface IShareOperation<R> extends IDataOperation<R>
        permits ShareOperation.Create, ShareOperation.CreateAll, ShareOperation.DeleteAllByIdInBatch, ShareOperation.RemoveByFatherShareId, ShareOperation.RemoveByFileIdIn, ShareOperation.SetFileNameByFileId, ShareOperation.UpdateSubShare, ShareOperation.removeByUserId {

}

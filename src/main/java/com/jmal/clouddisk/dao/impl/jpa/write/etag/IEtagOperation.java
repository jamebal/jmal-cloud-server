package com.jmal.clouddisk.dao.impl.jpa.write.etag;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IEtagOperation<R> extends IDataOperation<R>
        permits EtagOperation.ClearMarkUpdateById, EtagOperation.SetEtagByUserIdAndPathAndName, EtagOperation.SetFailedEtagById, EtagOperation.SetFoldersWithoutEtag, EtagOperation.SetMarkUpdateByUserIdAndPathAndName, EtagOperation.UpdateEtagAndSizeById {
}

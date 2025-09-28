package com.jmal.clouddisk.dao.write.directlink;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface IDirectLinkOperation<R> extends IDataOperation<R>
        permits DirectLinkOperation.CreateAll, DirectLinkOperation.DeleteByUserId, DirectLinkOperation.UpdateByFileId {

}

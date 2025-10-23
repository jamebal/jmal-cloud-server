package com.jmal.clouddisk.dao.impl.jpa.write.directlink;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IDirectLinkOperation<R> extends IDataOperation<R>
        permits DirectLinkOperation.CreateAll, DirectLinkOperation.DeleteByUserId, DirectLinkOperation.UpdateByFileId {

}

package com.jmal.clouddisk.dao.impl.jpa.write.ossconfig;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IOssConfigOperation<R> extends IDataOperation<R>
        permits OssConfigOperation.CreateAll, OssConfigOperation.DeleteById {

}

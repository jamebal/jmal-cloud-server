package com.jmal.clouddisk.dao.write.ossconfig;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface IOssConfigOperation<R> extends IDataOperation<R>
        permits OssConfigOperation.CreateAll, OssConfigOperation.DeleteById {

}

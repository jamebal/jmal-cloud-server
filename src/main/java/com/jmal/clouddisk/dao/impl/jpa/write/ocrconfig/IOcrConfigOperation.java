package com.jmal.clouddisk.dao.impl.jpa.write.ocrconfig;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IOcrConfigOperation<R> extends IDataOperation<R>
        permits OcrConfigOperation.CreateAll {

}

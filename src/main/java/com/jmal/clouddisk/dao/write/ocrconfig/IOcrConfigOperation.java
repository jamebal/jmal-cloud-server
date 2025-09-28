package com.jmal.clouddisk.dao.write.ocrconfig;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface IOcrConfigOperation<R> extends IDataOperation<R>
        permits OcrConfigOperation.CreateAll {

}

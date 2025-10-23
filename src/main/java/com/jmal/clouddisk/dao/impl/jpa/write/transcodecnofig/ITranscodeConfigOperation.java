package com.jmal.clouddisk.dao.impl.jpa.write.transcodecnofig;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface ITranscodeConfigOperation<R> extends IDataOperation<R>
        permits TranscodeConfigOperation.CreateAll {

}

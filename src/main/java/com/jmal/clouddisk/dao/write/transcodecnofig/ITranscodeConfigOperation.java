package com.jmal.clouddisk.dao.write.transcodecnofig;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface ITranscodeConfigOperation<R> extends IDataOperation<R>
        permits TranscodeConfigOperation.CreateAll {

}

package com.jmal.clouddisk.dao.impl.jpa.write.transcodecnofig;

import com.jmal.clouddisk.media.TranscodeConfig;

public final class TranscodeConfigOperation {
    private TranscodeConfigOperation() {}

    public record CreateAll(Iterable<TranscodeConfig> entities) implements ITranscodeConfigOperation<Void> {}

}

package com.jmal.clouddisk.dao.impl.jpa.write.ocrconfig;

import com.jmal.clouddisk.ocr.OcrConfig;

public final class OcrConfigOperation {
    private OcrConfigOperation() {}

    public record CreateAll(Iterable<OcrConfig> entities) implements IOcrConfigOperation<Void> {}

}

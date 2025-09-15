package com.jmal.clouddisk.dao.impl.jpa.write.ossconfig;

import com.jmal.clouddisk.oss.web.model.OssConfigDO;

public final class OssConfigOperation {
    private OssConfigOperation() {}

    public record CreateAll(Iterable<OssConfigDO> entities) implements IOssConfigOperation<Void> {}

    public record DeleteById(String id) implements IOssConfigOperation<Void> {}
}

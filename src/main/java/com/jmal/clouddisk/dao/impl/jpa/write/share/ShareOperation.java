package com.jmal.clouddisk.dao.impl.jpa.write.share;

import com.jmal.clouddisk.model.ShareDO;

public final class ShareOperation {
    private ShareOperation() {}

    public record CreateAll(Iterable<ShareDO> entities) implements IShareOperation {}
}

package com.jmal.clouddisk.dao.impl.jpa.write.trash;

import com.jmal.clouddisk.model.file.TrashEntityDO;

public final class TrashOperation {
    private TrashOperation() {}

    public record CreateAll(Iterable<TrashEntityDO> entities) implements ITrashOperation {}
}

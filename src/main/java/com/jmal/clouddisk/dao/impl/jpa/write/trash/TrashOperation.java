package com.jmal.clouddisk.dao.impl.jpa.write.trash;

import com.jmal.clouddisk.model.Trash;

import java.util.List;

public final class TrashOperation {
    private TrashOperation() {}

    public record CreateAll(List<Trash> trashes) implements ITrashOperation<Void> {}

    public record DeleteById(String trashFileId) implements ITrashOperation<Void> {}

    public record DeleteAll(List<String> ids) implements ITrashOperation<Void> {}
}

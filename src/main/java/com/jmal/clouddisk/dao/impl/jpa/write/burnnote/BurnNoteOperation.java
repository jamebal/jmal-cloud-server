package com.jmal.clouddisk.dao.impl.jpa.write.burnnote;

import com.jmal.clouddisk.model.BurnNoteDO;

import java.util.Collection;

public final class BurnNoteOperation {
    private BurnNoteOperation() {}

    public record Create(BurnNoteDO entities) implements IBurnNoteOperation<BurnNoteDO> {}

    public record Delete(String id) implements IBurnNoteOperation<Void> {}

    public record DeleteAllByIds(Collection<String> ids) implements IBurnNoteOperation<Integer> {}
}

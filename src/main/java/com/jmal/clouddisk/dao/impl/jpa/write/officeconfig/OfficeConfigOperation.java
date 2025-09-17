package com.jmal.clouddisk.dao.impl.jpa.write.officeconfig;

import com.jmal.clouddisk.office.model.OfficeConfigDO;

public final class OfficeConfigOperation {
    private OfficeConfigOperation() {}

    public record CreateAll(Iterable<OfficeConfigDO> entities) implements IOfficeConfigOperation<Void> {}

    public record Upsert(OfficeConfigDO officeConfigDO) implements IOfficeConfigOperation<Void> {}
}

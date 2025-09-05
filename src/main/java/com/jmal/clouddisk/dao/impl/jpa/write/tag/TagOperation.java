package com.jmal.clouddisk.dao.impl.jpa.write.tag;

import com.jmal.clouddisk.model.TagDO;

public final class TagOperation {
    private TagOperation() {}

    public record CreateAll(Iterable<TagDO> entities) implements ITagOperation {}
}

package com.jmal.clouddisk.dao.impl.jpa.write.tag;

import com.jmal.clouddisk.model.TagDO;

import java.util.List;

public final class TagOperation {
    private TagOperation() {}

    public record CreateAll(Iterable<TagDO> entities) implements ITagOperation<Void> {}
    public record Create(TagDO entity) implements ITagOperation<Void> {}
    public record RemoveByIdIn(List<String> tagIdList) implements ITagOperation<Void> {}
    public record UpdateSortById(String tagId, Integer sort) implements ITagOperation<Void> {}
}

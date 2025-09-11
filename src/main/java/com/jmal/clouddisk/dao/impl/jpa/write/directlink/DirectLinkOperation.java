package com.jmal.clouddisk.dao.impl.jpa.write.directlink;

import com.jmal.clouddisk.model.DirectLink;

public final class DirectLinkOperation {
    private DirectLinkOperation() {}

    public record CreateAll(Iterable<DirectLink> entities) implements IDirectLinkOperation<Void> {}
    public record DeleteByUserId(String userId) implements IDirectLinkOperation<Void> {}
    public record UpdateByFileId(String fileId, String mark, String userId, java.time.LocalDateTime now) implements IDirectLinkOperation<Void> {}
}

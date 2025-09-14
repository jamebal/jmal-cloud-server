package com.jmal.clouddisk.dao.impl.jpa.write.share;

import com.jmal.clouddisk.model.ShareDO;

import java.util.List;

public final class ShareOperation {
    private ShareOperation() {}

    public record CreateAll(Iterable<ShareDO> entities) implements IShareOperation<Void> {}
    public record RemoveByFileIdIn(List<String> fileIdList) implements IShareOperation<Void> {}
    public record DeleteAllByIdInBatch(List<String> roleIdList) implements IShareOperation<Void> {}
    public record RemoveByFatherShareId(String fatherShareId) implements IShareOperation<Void> {}
    public record removeByUserId(String userId) implements IShareOperation<Void> {}
    public record Create(ShareDO entity) implements IShareOperation<ShareDO> {}
    public record UpdateSubShare(List<String> subShareFileIdList, String id, Boolean isPrivacy, String extractionCode) implements IShareOperation<Void> {}

    public record SetFileNameByFileId(String fileId, String newFileName) implements IShareOperation<Void> {}
}

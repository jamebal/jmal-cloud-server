package com.jmal.clouddisk.dao.impl.jpa.write.etag;

import java.time.Instant;

public final class EtagOperation {
    private EtagOperation() {}

    public record SetFoldersWithoutEtag() implements IEtagOperation<Integer>{}

    public record SetEtagByUserIdAndPathAndName(String userId, String path, String name, String newEtag) implements IEtagOperation<Void>{}

    public record ClearMarkUpdateById(String fileId) implements IEtagOperation<Void>{}

    public record SetMarkUpdateByUserIdAndPathAndName(String userId, String path, String name) implements IEtagOperation<Integer>{}

    public record UpdateEtagAndSizeById(String fileId, String etag, long size, int childrenCount) implements IEtagOperation<Integer>{}

    public record SetFailedEtagById(String fileId, int attempts, String errorMsg, Boolean needsEtagUpdate) implements IEtagOperation<Void>{}

    public record setRetryAtById(String fileId, Instant nextRetryTime, int attempts) implements IEtagOperation<Void>{}
}

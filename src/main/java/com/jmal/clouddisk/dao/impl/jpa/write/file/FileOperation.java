package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import com.jmal.clouddisk.model.file.ShareProperties;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public final class FileOperation {
    private FileOperation() {}

    public record Default() implements IFileOperation<Void> {}

    public record CreateFileMetadata(FileMetadataDO entity) implements IFileOperation<FileMetadataDO> {}
    public record CreateAllFileMetadata(Iterable<FileMetadataDO> entities) implements IFileOperation<Integer> {}
    public record DeleteById(String fileId) implements IFileOperation<Void> {}

    public record SetShareBaseOperation(String fileId) implements IFileOperation<Void> {}
    public record UnsetShareBaseOperation(String fileId) implements IFileOperation<Void> {}
    public record UpdateTagsForFile(String fileId, Set<Tag> tags) implements IFileOperation<Void> {}

    public record DeleteAllByIdInBatch(List<String> fileIdList) implements IFileOperation<Void> {}
    public record RemoveAllByUserIdAndPathPrefix(String userId, String pathPrefix) implements IFileOperation<Integer> {}
    public record RemoveByUserIdAndPathAndName(String userId, String path, String name) implements IFileOperation<Void> {}

    public record DeleteAllByUserIdInBatch(List<String> userIdList) implements IFileOperation<Void> {}

    public record RemoveByMountFileId(String fileId) implements IFileOperation<Void> {}

    public record UpdateFileSize(String fileId, Long size) implements IFileOperation<Integer> {}
    public record ClearAllFolderSizes() implements IFileOperation<Integer> {}

    public record UpdateShareProps(
            @Param("fileId") String fileId,
            @Param("userId") String userId,
            @Param("pathPrefix") String pathPrefix,
            @Param("shareId") String shareId,
            @Param("shareProps") ShareProperties shareProps,
            @Param("isFolder") Boolean isFolder) implements IFileOperation<Integer> {}
    public record UpdateShareBaseById(String fileId, Boolean shareBase) implements IFileOperation<Integer> {}

    public record UnsetShareProps(String fileId, String userId, String pathPrefixForLike, ShareProperties shareProperties,
                                  boolean isFolder) implements IFileOperation<Integer> {}

    public record SetSubShareFormShareBase(String userId, String pathPrefixForLike) implements IFileOperation<Integer> {}

    public record UpdateModifyFile(String id, long length, String md5, String suffix, String fileContentType,
                                   LocalDateTime updateTime) implements IFileOperation<Long> {}

    public record UnsetDelTag(String fileId) implements IFileOperation<Long> {}

    public record SetIsFavoriteByIdIn(List<String> fileIds, boolean isFavorite) implements IFileOperation<Void> {}

    public record SetNameAndSuffixById(String fileId, String name, String suffix) implements IFileOperation<Void> {}
}

package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.model.file.FileMetadataDO;
import com.jmal.clouddisk.model.file.OtherProperties;
import com.jmal.clouddisk.model.file.ShareProperties;
import com.jmal.clouddisk.model.file.dto.UpdateFile;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public final class FileOperation {
    private FileOperation() {}

    public record Default() implements IFileOperation<Void> {}

    public record CreateFileMetadata(FileDocument entity) implements IFileOperation<FileMetadataDO> {}
    public record CreateAllFileMetadata(List<FileMetadataDO> entities) implements IFileOperation<Integer> {}
    public record DeleteById(String fileId) implements IFileOperation<Void> {}

    public record SetShareBaseOperation(String fileId) implements IFileOperation<Void> {}
    public record UnsetShareBaseOperation(String fileId) implements IFileOperation<Void> {}
    public record UpdateTagsForFile(String fileId, List<Tag> tags) implements IFileOperation<Void> {}
    public record UpdateTagsForFiles(List<String> fileIds, List<Tag> tags) implements IFileOperation<Void> {}

    public record DeleteAllByIdInBatch(List<String> fileIdList) implements IFileOperation<Void> {}
    public record RemoveAllByUserIdAndPathPrefix(String userId, String pathPrefix) implements IFileOperation<Integer> {}
    public record RemoveByUserIdAndPathAndName(String userId, String path, String name) implements IFileOperation<Void> {}

    public record DeleteAllByUserIdInBatch(List<String> userIdList) implements IFileOperation<Void> {}

    public record RemoveByMountFileIdIn(List<String> fileIds) implements IFileOperation<Void> {}

    public record UpdateFileSize(String fileId, Long size) implements IFileOperation<Integer> {}
    public record ClearAllFolderSizes() implements IFileOperation<Integer> {}

    public record UpdateShareProps(
            @Param("fileId") String fileId,
            @Param("userId") String userId,
            @Param("pathPrefix") String pathPrefix,
            @Param("shareId") String shareId,
            @Param("shareProps") ShareProperties shareProps,
            @Param("isFolder") Boolean isFolder) implements IFileOperation<Integer> {}
    public record UpdateShareBaseById(String fileId, String shareId, ShareProperties shareProps, Boolean shareBase) implements IFileOperation<Integer> {}

    public record UnsetShareProps(String fileId, String userId, String pathPrefixForLike, ShareProperties shareProperties,
                                  boolean isFolder) implements IFileOperation<Integer> {}

    public record SetSubShareFormShareBase(String userId, String pathPrefixForLike) implements IFileOperation<Integer> {}

    public record UpdateModifyFile(String id, long length, String md5, String suffix, String fileContentType,
                                   LocalDateTime updateTime) implements IFileOperation<Integer> {}

    public record UnsetDelTag(String fileId) implements IFileOperation<Integer> {}

    public record SetIsFavoriteByIdIn(List<String> fileIds, boolean isFavorite) implements IFileOperation<Void> {}

    public record SetNameAndSuffixById(String fileId, String name, String suffix) implements IFileOperation<Void> {}

    public record SetContent(String id, byte[] content) implements IFileOperation<Void> {}

    public record SetOtherPropsById(String id, OtherProperties otherProperties) implements IFileOperation<Void> {}

    public record SetPathById(String id, String newFilePath) implements IFileOperation<Void> {}

    public record SetNameByMountFileId(String fileId, String newFileName) implements IFileOperation<Void> {}

    public record UpdateFileByUserIdAndPathAndName(String userId, String path, String name, UpdateFile updateFile) implements IFileOperation<Void> {}

    public record UpsertByUserIdAndPathAndName(String userId, String path, String name, FileDocument fileDocument) implements IFileOperation<String> {}

    public record SetUpdateDateById(String fileId, LocalDateTime time) implements IFileOperation<Void> {}

    public record UpdateSharePropsById(String fileId, ShareProperties shareProps) implements IFileOperation<Void> {}

    public record UnsetTranscodeVideo() implements IFileOperation<Void> {}

    public record UpdateTranscodeVideoByIdIn(List<String> fileIdList, int status) implements IFileOperation<Integer> {}

    public record setOtherPropsByUserIdAndPathAndName(OtherProperties otherProperties, String userId, String path, String name) implements IFileOperation<Void> {}

    public record UpdateLuceneIndexStatusByIdIn(List<String> fileIdList, int indexStatus) implements IFileOperation<Void> {}

    public record UnsetDelTagByIdIn(List<String> fileIdList) implements IFileOperation<Void> {}

    public record SetDelTag(String userId, String path) implements IFileOperation<Void> {}

    public record ResetIndexStatus() implements IFileOperation<Void> {}
}

package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.file.ArticleDO;
import com.jmal.clouddisk.model.file.FileMetadataDO;

import java.util.List;
import java.util.Set;

public final class FileOperation {
    private FileOperation() {}

    public record Default() implements IFileOperation<Void> {}

    public record CreateFileMetadata(FileMetadataDO entity) implements IFileOperation<FileMetadataDO> {}
    public record CreateAllArticle(Iterable<ArticleDO> entities) implements IFileOperation<Integer> {}
    public record CreateAllFileMetadata(Iterable<FileMetadataDO> entities) implements IFileOperation<Integer> {}
    public record DeleteById(String fileId) implements IFileOperation<Void> {}

    public record SetShareBaseOperation(String fileId) implements IFileOperation<Void> {}
    public record UnsetShareBaseOperation(String fileId) implements IFileOperation<Void> {}
    public record UpdateTagsForFile(String fileId, Set<Tag> tags) implements IFileOperation<Void> {}

    public record DeleteAllByIdInBatch(List<String> fileIdList) implements IFileOperation<Void> {}

    public record DeleteAllByUserIdInBatch(List<String> userIdList) implements IFileOperation<Void> {}

    public record RemoveByMountFileId(String fileId) implements IFileOperation<Void> {}

    public record UpdateFileSize(String fileId, Long size) implements IFileOperation<Integer> {}
    public record ClearAllFolderSizes() implements IFileOperation<Integer> {}

}

package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.model.Tag;
import com.jmal.clouddisk.model.file.ArticleDO;
import com.jmal.clouddisk.model.file.FileMetadataDO;

import java.util.Set;

public final class FileOperation {
    private FileOperation() {}

    public record CreateFileMetadata(FileMetadataDO entity) implements IFileOperation {}
    public record CreateAllArticle(Iterable<ArticleDO> entities) implements IFileOperation {}
    public record CreateAllFileMetadata(Iterable<FileMetadataDO> entities) implements IFileOperation {}
    public record DeleteById(FileMetadataDO entity) implements IFileOperation {}

    public record SetShareBaseOperation(String fileId) implements IFileOperation {}
    public record UnsetShareBaseOperation(String fileId) implements IFileOperation {}
    public record UpdateTagsForFile(String fileId, Set<Tag> tags) implements IFileOperation {}

}

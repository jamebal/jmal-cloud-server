package com.jmal.clouddisk.dao.impl.jpa.write.filehistory;

import com.jmal.clouddisk.model.file.FileHistoryDO;

import java.io.InputStream;
import java.util.List;

public final class FileHistoryOperation {
    private FileHistoryOperation() {}

    public record CreateAll(Iterable<FileHistoryDO> entities) implements IFileHistoryOperation<Void> {}
    public record Create(FileHistoryDO entity, InputStream inputStream) implements IFileHistoryOperation<Void> {}

    public record DeleteByFileIds(List<String> fileIds) implements IFileHistoryOperation<Void> {}

    public record DeleteByIds(List<String> fileHistoryIds) implements IFileHistoryOperation<Void> {}

    public record UpdateFileId(String sourceFileId, String destinationFileId) implements IFileHistoryOperation<Void> {}
}

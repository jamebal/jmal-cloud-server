package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IFileOperation<R> extends IDataOperation<R>
        permits FileOperation.CreateAllArticle, FileOperation.CreateAllFileMetadata, FileOperation.CreateFileMetadata, FileOperation.Default, FileOperation.DeleteAllByIdInBatch, FileOperation.DeleteAllByUserIdInBatch, FileOperation.DeleteById, FileOperation.RemoveByMountFileId, FileOperation.SetShareBaseOperation, FileOperation.UnsetShareBaseOperation, FileOperation.UpdateTagsForFile {
}

package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IFileOperation<R> extends IDataOperation<R>
        permits FileOperation.ClearAllFolderSizes, FileOperation.CreateAllFileMetadata, FileOperation.CreateFileMetadata, FileOperation.Default, FileOperation.DeleteAllByIdInBatch, FileOperation.DeleteAllByUserIdInBatch, FileOperation.DeleteById, FileOperation.RemoveAllByUserIdAndPathPrefix, FileOperation.RemoveByMountFileId, FileOperation.RemoveByUserIdAndPathAndName, FileOperation.SetIsFavoriteByIdIn, FileOperation.SetNameAndSuffixById, FileOperation.SetShareBaseOperation, FileOperation.SetSubShareFormShareBase, FileOperation.UnsetDelTag, FileOperation.UnsetShareBaseOperation, FileOperation.UnsetShareProps, FileOperation.UpdateFileSize, FileOperation.UpdateModifyFile, FileOperation.UpdateShareBaseById, FileOperation.UpdateShareProps, FileOperation.UpdateTagsForFile {
}

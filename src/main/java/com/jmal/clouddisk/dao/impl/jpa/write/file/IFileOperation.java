package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IFileOperation extends IDataOperation
        permits FileOperation.CreateAllArticle,
        FileOperation.CreateAllFileMetadata,
        FileOperation.CreateFileMetadata,
        FileOperation.DeleteById,
        FileOperation.SetShareBaseOperation,
        FileOperation.UnsetShareBaseOperation {
}

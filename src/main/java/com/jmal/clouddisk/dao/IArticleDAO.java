package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.file.FileDocument;

public interface IArticleDAO {

    void createArticleFromDocument(FileDocument fileDocument);
}

package com.jmal.clouddisk.dao.impl.jpa.write.article;

import com.jmal.clouddisk.model.file.ArticleDO;
import com.jmal.clouddisk.model.file.FileDocument;

public final class ArticleOperation {
    private ArticleOperation() {}

    public record CreateAll(Iterable<ArticleDO> entities) implements IArticleOperation<Void> {}
    public record Create(FileDocument fileDocument) implements IArticleOperation<ArticleDO> {}
    public record UpdatePageSortById(String id, Integer pageSort) implements IArticleOperation<Void> {}
    public record updateHasDraftById(String id, Boolean hasDraft) implements IArticleOperation<Void> {}
}

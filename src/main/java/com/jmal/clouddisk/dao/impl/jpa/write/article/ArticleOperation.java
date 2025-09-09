package com.jmal.clouddisk.dao.impl.jpa.write.article;

import com.jmal.clouddisk.model.file.ArticleDO;

public final class ArticleOperation {
    private ArticleOperation() {}

    public record CreateAll(Iterable<ArticleDO> entities) implements IArticleOperation<Void> {}
    public record UpdatePageSortById(String id, Integer pageSort) implements IArticleOperation<Void> {}
}

package com.jmal.clouddisk.dao.impl.jpa.write.article;

import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperation;

public sealed interface IArticleOperation<R> extends IDataOperation<R>
        permits ArticleOperation.CreateAll, ArticleOperation.UpdatePageSortById {

}

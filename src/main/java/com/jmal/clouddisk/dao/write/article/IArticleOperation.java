package com.jmal.clouddisk.dao.write.article;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface IArticleOperation<R> extends IDataOperation<R>
        permits ArticleOperation.Create, ArticleOperation.CreateAll, ArticleOperation.UpdatePageSortById, ArticleOperation.updateHasDraftById {

}

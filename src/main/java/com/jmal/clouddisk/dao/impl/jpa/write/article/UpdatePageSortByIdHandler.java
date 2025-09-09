package com.jmal.clouddisk.dao.impl.jpa.write.article;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.ArticleRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("articleUpdatePageSortByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdatePageSortByIdHandler implements IDataOperationHandler<ArticleOperation.UpdatePageSortById, Void> {

    private final ArticleRepository repo;

    @Override
    public Void handle(ArticleOperation.UpdatePageSortById op) {
        repo.updatePageSortById(op.id(), op.pageSort());
        return null;
    }
}

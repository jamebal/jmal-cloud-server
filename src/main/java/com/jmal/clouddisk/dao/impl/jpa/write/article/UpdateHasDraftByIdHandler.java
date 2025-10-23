package com.jmal.clouddisk.dao.impl.jpa.write.article;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.ArticleRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("articleUpdateHasDraftByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class UpdateHasDraftByIdHandler implements IDataOperationHandler<ArticleOperation.updateHasDraftById, Void> {

    private final ArticleRepository repo;

    @Override
    public Void handle(ArticleOperation.updateHasDraftById op) {
        repo.updateHasDraftById(op.id(), op.hasDraft());
        return null;
    }
}

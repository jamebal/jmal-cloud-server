package com.jmal.clouddisk.dao.impl.jpa.write.article;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.ArticleRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("articleCreateAllHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllHandler implements IDataOperationHandler<ArticleOperation.CreateAll, Void> {

    private final ArticleRepository repo;

    @Override
    public Void handle(ArticleOperation.CreateAll op) {
        repo.saveAll(op.entities());
        return null;
    }
}

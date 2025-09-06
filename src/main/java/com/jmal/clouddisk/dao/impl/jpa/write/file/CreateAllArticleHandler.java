package com.jmal.clouddisk.dao.impl.jpa.write.file;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.ArticleRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("fileCreateAllArticleHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class CreateAllArticleHandler implements IDataOperationHandler<FileOperation.CreateAllArticle, Integer> {

    private final ArticleRepository repo;

    @Override
    public Integer handle(FileOperation.CreateAllArticle op) {
        return repo.saveAll(op.entities()).size();
    }
}

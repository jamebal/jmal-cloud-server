package com.jmal.clouddisk.dao.impl.jpa.write.searchhistory;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.SearchHistoryRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("searchHistoryRemoveByIdHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class RemoveByIdHandler implements IDataOperationHandler<SearchHistoryOperation.RemoveById, Void> {

    private final SearchHistoryRepository repo;

    @Override
    public Void handle(SearchHistoryOperation.RemoveById op) {
        repo.deleteById(op.id());
        return null;
    }
}

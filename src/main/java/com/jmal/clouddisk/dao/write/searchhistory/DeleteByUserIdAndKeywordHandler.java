package com.jmal.clouddisk.dao.write.searchhistory;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.repository.jpa.SearchHistoryRepository;
import com.jmal.clouddisk.dao.write.IDataOperationHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("searchHistoryDeleteByUserIdAndKeywordHandler")
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class DeleteByUserIdAndKeywordHandler implements IDataOperationHandler<SearchHistoryOperation.DeleteByUserIdAndKeyword, Void> {

    private final SearchHistoryRepository repo;

    @Override
    public Void handle(SearchHistoryOperation.DeleteByUserIdAndKeyword op) {
        repo.deleteByUserIdAndKeyword(op.userId(), op.keyword());
        return null;
    }
}

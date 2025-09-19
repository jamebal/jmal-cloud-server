package com.jmal.clouddisk.dao.impl.jpa.write.searchhistory;

import com.jmal.clouddisk.model.query.SearchOptionHistoryDO;

public final class SearchHistoryOperation {
    private SearchHistoryOperation() {}

    public record CreateAll(Iterable<SearchOptionHistoryDO> entities) implements ISearchHistoryOperation<Void> {}

    public record DeleteByUserIdAndKeyword(String keyword, String userId) implements ISearchHistoryOperation<Void> {}

    public record RemoveByUserIdAndSearchTimeLessThan(String userId, long time) implements ISearchHistoryOperation<Void> {}

    public record RemoveById(String id) implements ISearchHistoryOperation<Void> {}

    public record RemoveAllByUserId(String userId) implements ISearchHistoryOperation<Void> {}
}

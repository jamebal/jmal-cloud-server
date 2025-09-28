package com.jmal.clouddisk.dao.write.searchhistory;

import com.jmal.clouddisk.dao.write.IDataOperation;

public sealed interface ISearchHistoryOperation<R> extends IDataOperation<R>
        permits SearchHistoryOperation.CreateAll, SearchHistoryOperation.DeleteByUserIdAndKeyword, SearchHistoryOperation.RemoveAllByUserId, SearchHistoryOperation.RemoveById, SearchHistoryOperation.RemoveByUserIdAndSearchTimeLessThan {
}

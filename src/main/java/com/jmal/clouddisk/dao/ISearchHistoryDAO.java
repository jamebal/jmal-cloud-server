package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.query.SearchOptionHistoryDO;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ISearchHistoryDAO {

    void removeByUserIdAndKeyword(String keyword, String userId);

    void removeByUserIdAndSearchTimeLessThan(String userId, long time);

    void save(SearchOptionHistoryDO searchOptionHistoryDO);

    List<SearchOptionHistoryDO> findRecentByUserIdAndKeyword(String userId, String keyword, Pageable pageable);

    void removeById(String id);

    void removeAllByUserId(String userId);
}

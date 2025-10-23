package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.ISearchHistoryDAO;
import com.jmal.clouddisk.dao.impl.jpa.repository.SearchHistoryRepository;
import com.jmal.clouddisk.dao.impl.jpa.write.IWriteService;
import com.jmal.clouddisk.dao.impl.jpa.write.searchhistory.SearchHistoryOperation;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.query.SearchOptionHistoryDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class SearchHistoryJpaImpl implements ISearchHistoryDAO, IWriteCommon<SearchOptionHistoryDO> {

    private final IWriteService writeService;

    private final SearchHistoryRepository searchHistoryRepository;

    @Override
    public void AsyncSaveAll(Iterable<SearchOptionHistoryDO> entities) {
        writeService.submit(new SearchHistoryOperation.CreateAll(entities));
    }

    @Override
    public void removeByUserIdAndKeyword(String keyword, String userId) {
        try {
            writeService.submit(new SearchHistoryOperation.DeleteByUserIdAndKeyword(keyword, userId)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void removeByUserIdAndSearchTimeLessThan(String userId, long time) {
        try {
            writeService.submit(new SearchHistoryOperation.RemoveByUserIdAndSearchTimeLessThan(userId, time)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void save(SearchOptionHistoryDO searchOptionHistoryDO) {
        try {
            writeService.submit(new SearchHistoryOperation.CreateAll(Collections.singleton(searchOptionHistoryDO))).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public List<SearchOptionHistoryDO> findRecentByUserIdAndKeyword(String userId, String keyword, Pageable pageable) {
        return searchHistoryRepository.findByUserIdAndKeywordLike(userId, "%" + keyword + "%", pageable);
    }

    @Override
    public void removeById(String id) {
        try {
            writeService.submit(new SearchHistoryOperation.RemoveById(id)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }

    @Override
    public void removeAllByUserId(String userId) {
        try {
            writeService.submit(new SearchHistoryOperation.RemoveAllByUserId(userId)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new CommonException(e.getMessage());
        }
    }
}

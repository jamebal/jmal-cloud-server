package com.jmal.clouddisk.dao.repository.jpa;

import com.jmal.clouddisk.model.query.SearchOptionHistoryDO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SearchHistoryRepository extends JpaRepository<SearchOptionHistoryDO, String> {

    void deleteByUserIdAndKeyword(String userId, String keyword);

    void deleteByUserIdAndSearchTimeLessThan(String userId, Long searchTimeIsLessThan);

    @Query("select s from SearchOptionHistoryDO s " +
            "WHERE " +
            "s.userId = :userId AND" +
            "(:keyword IS NULL OR s.keyword LIKE :keyword ESCAPE '\\')"
    )
    List<SearchOptionHistoryDO> findByUserIdAndKeywordLike(String userId, String keyword, Pageable pageable);

    @Modifying
    @Query("delete from SearchOptionHistoryDO s where s.userId = :userId")
    void deleteAllByUserId(String userId);
}

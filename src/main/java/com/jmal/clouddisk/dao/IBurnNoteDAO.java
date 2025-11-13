package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.BurnNoteDO;
import com.jmal.clouddisk.model.dto.BurnNoteVO;
import com.jmal.clouddisk.model.query.QueryBaseDTO;

import java.util.List;

/**
 * 阅后即焚笔记 DAO 接口
 * 提供统一的数据访问方法,支持多种数据库实现
 */
public interface IBurnNoteDAO {

    /**
     * 保存或更新笔记
     */
    BurnNoteDO save(BurnNoteDO burnNoteDO);

    /**
     * 根据笔记ID查找
     */
    BurnNoteDO findById(String id);

    /**
     * 根据笔记ID删除
     */
    void deleteById(String id);

    /**
     * 删除已过期的笔记
     */
    long deleteExpiredNotes();

    boolean existData();

    List<BurnNoteVO> findAll(QueryBaseDTO queryBaseDTO);

    List<BurnNoteVO> findAllByUserId(QueryBaseDTO queryBaseDTO, String userId);
}

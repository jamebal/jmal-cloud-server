package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.HeartwingsDO;
import com.jmal.clouddisk.util.ResponseResult;

import java.util.List;

public interface IHeartwingsDAO {

    void save(HeartwingsDO heartwingsDO);

    ResponseResult<List<HeartwingsDO>> getWebsiteHeartwings(Integer page, Integer pageSize, String order);

}

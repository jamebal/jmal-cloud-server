package com.jmal.clouddisk.oss.web;

import com.jmal.clouddisk.oss.PlatformOSS;
import com.jmal.clouddisk.util.ResponseResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jmal
 * @Description IOssWebService
 * @date 2023/4/4 15:57
 */
public interface IOssWebService {

    /**
     * 获取appToken
     *
     * @return STSObjectVO
     */
    ResponseResult<STSObjectVO> getAppToken();

    default List<Map<String, String>> getPlatformList() {
        List<Map<String, String>> maps = new ArrayList<>(PlatformOSS.values().length);
        for (PlatformOSS platformOSS : PlatformOSS.values()) {
            Map<String, String> map = new HashMap<>(2);
            map.put("value", platformOSS.getKey());
            map.put("label", platformOSS.getValue());
            maps.add(map);
        }
        return maps;
    }
}

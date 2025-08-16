package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;


/**
 * @author jmal
 * @Description 网盘设置
 * @Date 2022/03/26 2:45 下午
 */
@Data
public class CloudSettingDO implements Reflective {
    /**
     * 网盘名称
     */
    private String cloudName;
    /**
     * 网盘logo
     */
    private byte[] logo;
}

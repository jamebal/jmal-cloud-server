package com.jmal.clouddisk.model;

import lombok.Data;

import java.util.List;

/**
 * @author jmal
 * @Description 网盘设置
 * @Date 2022/03/26 2:45 下午
 */
@Data
public class CloudSettingDO {
    /**
     * 网盘名称
     */
    private String cloudName;
    /**
     * 网盘logo
     */
    private byte[] logo;
}

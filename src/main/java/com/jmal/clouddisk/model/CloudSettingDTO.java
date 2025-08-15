package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author jmal
 * @Description 网盘设置DTO
 * @Date 2022/03/26 2:45 下午
 */
@Data
public class CloudSettingDTO implements Reflective {
    /**
     * 网盘名称
     */
    private String cloudName;
    /**
     * 网盘logo
     */
    private byte[] logo;

    private MultipartFile file;
}

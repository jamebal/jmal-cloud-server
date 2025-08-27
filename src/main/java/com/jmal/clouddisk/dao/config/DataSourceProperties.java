package com.jmal.clouddisk.dao.config;

import com.jmal.clouddisk.dao.DataSourceType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "jmalcloud.datasource")
public class DataSourceProperties {

    @NotNull(message = "数据源类型不能为空")
    private DataSourceType type;

    /**
     * 是否在启动时进行数据迁移，仅在type为jpa时有效(从mongodb迁移到jpa)
     */
    private Boolean migration = false;

    /**
     * 是否在启动时验证数据源连接
     */
    private boolean validateOnStartup = true;

    /**
     * 数据源描述信息
     */
    private String description;
}

package com.jmal.clouddisk.config.jpa;

import com.jmal.clouddisk.dao.DataSourceType;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 关系型数据源条件判断 - 排除 SQLite
 * @author jamebal
 */
public class RelationalNotSqliteDataSourceCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String dataSourceTypeStr = context.getEnvironment().getProperty("jmalcloud.datasource.type");

        if (dataSourceTypeStr == null) {
            return ConditionOutcome.noMatch("jmalcloud.datasource.type 属性未配置");
        }

        try {
            DataSourceType dataSourceType = DataSourceType.fromCode(dataSourceTypeStr);

            if (dataSourceType.isRelational() && dataSourceType != DataSourceType.sqlite) {
                return ConditionOutcome.match("关系型数据源匹配: " + dataSourceType.getCode());
            } else {
                return ConditionOutcome.noMatch("非关系型数据源: " + dataSourceType.getCode());
            }
        } catch (IllegalArgumentException e) {
            return ConditionOutcome.noMatch("不支持的数据源类型: " + dataSourceTypeStr);
        }
    }
}

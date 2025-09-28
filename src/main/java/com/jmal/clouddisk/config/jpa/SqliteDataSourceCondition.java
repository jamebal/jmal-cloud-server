package com.jmal.clouddisk.config.jpa;

import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.dao.DataSourceType;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 关系型数据源条件判断 - 排除 SQLite
 * @author jamebal
 */
public class SqliteDataSourceCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Boolean jpaEnable = context.getEnvironment().getProperty("jmalcloud.datasource.jpa-enabled", Boolean.class, false);
        String dataSourceTypeStr = context.getEnvironment().getProperty("jmalcloud.datasource.type");

        try {
            DataSourceType dataSourceType = DataSourceType.fromCode(dataSourceTypeStr);

            if (BooleanUtil.isTrue(jpaEnable) && dataSourceType == DataSourceType.sqlite) {
                return ConditionOutcome.match("SQLite关系型数据源匹配: " + dataSourceType.getCode());
            } else {
                return ConditionOutcome.noMatch("非SQLite关系型数据源: " + dataSourceType.getCode());
            }
        } catch (IllegalArgumentException e) {
            return ConditionOutcome.noMatch("不支持的数据源类型");
        }
    }
}

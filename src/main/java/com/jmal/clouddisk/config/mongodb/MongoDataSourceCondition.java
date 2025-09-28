package com.jmal.clouddisk.config.mongodb;

import cn.hutool.core.util.BooleanUtil;
import com.jmal.clouddisk.dao.DataSourceType;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * MongoDB条件判断
 */
public class MongoDataSourceCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String dataSourceTypeStr = context.getEnvironment().getProperty("jmalcloud.datasource.type");
        Boolean migration = context.getEnvironment().getProperty("jmalcloud.datasource.migration", Boolean.class, false);
        if (dataSourceTypeStr == null) {
            return ConditionOutcome.noMatch("jmalcloud.datasource.type 属性未配置");
        }

        try {
            DataSourceType dataSourceType = DataSourceType.fromCode(dataSourceTypeStr);

            if (dataSourceType == DataSourceType.mongodb || BooleanUtil.isTrue(migration)) {
                return ConditionOutcome.match("MongoDB数据源匹配: " + dataSourceType.getCode());
            } else {
                return ConditionOutcome.noMatch("非MongoDB数据源: " + dataSourceType.getCode());
            }
        } catch (IllegalArgumentException e) {
            return ConditionOutcome.noMatch("不支持的数据源类型: " + dataSourceTypeStr);
        }
    }
}

package com.jmal.clouddisk.config.jpa;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 关系型数据源条件判断
 * @author jamebal
 */
public class RelationalDataSourceCondition extends SpringBootCondition {

    @Override
    public ConditionOutcome getMatchOutcome(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Boolean jpaEnable = context.getEnvironment().getProperty("jmalcloud.datasource.jpa-enabled", Boolean.class, false);

        try {
            if (BooleanUtil.isTrue(jpaEnable)) {
                return ConditionOutcome.match("关系型数据源匹配");
            } else {
                return ConditionOutcome.noMatch("非关系型数据源");
            }
        } catch (IllegalArgumentException e) {
            return ConditionOutcome.noMatch("不支持的数据源类型");
        }
    }
}

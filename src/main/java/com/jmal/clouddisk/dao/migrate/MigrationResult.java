package com.jmal.clouddisk.dao.migrate;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Data
@ConditionalOnProperty(name = "jmalcloud.datasource.migration")
@Conditional(RelationalDataSourceCondition.class)
public class MigrationResult {
    private String name;
    private Integer totalProcessed;
    private Integer successCount;
    private Integer errorCount;
    private List<String> errors;
    private String fatalError;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    public MigrationResult(String name) {
        this.name = name;
        this.totalProcessed = 0;
        this.successCount = 0;
        this.errorCount = 0;
        this.startTime = LocalDateTime.now();
        this.errors = new java.util.ArrayList<>();
    }

    public void addProcessed(Integer count) {
        totalProcessed += count;
    }

    public void addSuccess(Integer count) {
        if (successCount == null) {
            successCount = 0;
        }
        successCount += count;
    }

    public void setFatalError(String error) {
        this.fatalError = error;
        this.endTime = LocalDateTime.now();
    }

    public double getDurationInSeconds() { // 建议改个名字以示区别
        if (endTime == null) {
            endTime = LocalDateTime.now();
        }
        Duration duration = Duration.between(startTime, endTime);
        // 将总毫秒数转换为秒，保留小数部分
        return duration.toMillis() / 1000.0;
    }

    @Override
    public String toString() {
        return String.format(
                "%s数据 迁移结果 - 总处理: %d, 成功: %d, 失败: %d, 耗时: %.3f秒, 错误数: %d",
                name, totalProcessed, successCount, errorCount, getDurationInSeconds(), errors.size()
        );
    }
}

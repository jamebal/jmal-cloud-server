package com.jmal.clouddisk.dao.mongo_to_jpa;

import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Conditional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.migration")
@Conditional(RelationalDataSourceCondition.class)
public class MigrationResult {
    private int totalProcessed = 0;
    private int successCount = 0;
    private int errorCount = 0;
    private List<String> errors = new ArrayList<>();
    private String fatalError;
    private LocalDateTime startTime = LocalDateTime.now();
    private LocalDateTime endTime;

    public void incrementProcessed() {
        totalProcessed++;
    }

    public void addProcessed(int count) {
        totalProcessed += count;
    }

    public void addSuccess(int count) {
        successCount += count;
    }

    public void addError(String id, String error) {
        errorCount++;
        errors.add(String.format("ID: %s, Error: %s", id, error));
    }

    public void setFatalError(String error) {
        this.fatalError = error;
        this.endTime = LocalDateTime.now();
    }

    @Override
    public String toString() {
        endTime = LocalDateTime.now();
        Duration duration = Duration.between(startTime, endTime);

        return String.format(
            "迁移结果 - 总处理: %d, 成功: %d, 失败: %d, 耗时: %d秒, 错误数: %d",
            totalProcessed, successCount, errorCount, duration.getSeconds(), errors.size()
        );
    }
}

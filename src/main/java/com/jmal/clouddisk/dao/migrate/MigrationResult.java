package com.jmal.clouddisk.dao.migrate;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
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
    private String name;
    private int totalProcessed = 0;
    private int successCount = 0;
    private int errorCount = 0;
    private List<String> errors = new ArrayList<>();
    private String fatalError;
    private LocalDateTime startTime = LocalDateTime.now();
    private LocalDateTime endTime;

    public MigrationResult(String name) {
        this.name = name;
    }

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

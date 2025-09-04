package com.jmal.clouddisk.config.jpa;

import com.jmal.clouddisk.config.FileProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "sqlite")
public class SqliteDataSource {

    private final FileProperties fileProperties;

    @Bean
    public DataSource dataSource() {
        Path dbPath = Paths.get(fileProperties.getRootDir(), fileProperties.getJmalcloudDBDir(), "db");
        ensureDirectoryExists(dbPath);
        Path dbDataPath = Paths.get(fileProperties.getRootDir(), fileProperties.getJmalcloudDBDir(), "data");
        ensureDirectoryExists(dbDataPath);

        String url = "jdbc:sqlite:" + dbPath.resolve("jmalcloud.db");

        SQLiteConfig config = new SQLiteConfig();

        // 强制开启WAL模式 (最关键)
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);

        // 强制设置合理的超时时间
        config.setBusyTimeout(5000); // 5秒

        // 强制设置为NORMAL锁定模式，确保WAL可以工作
        config.setLockingMode(SQLiteConfig.LockingMode.NORMAL);

        // 在WAL模式下，推荐的同步级别
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);

        // config.setPageSize(4096);
        // config.setCacheSize(10000);

        SQLiteDataSource ds = new SQLiteDataSource();
        ds.setUrl(url);

        ds.setConfig(config);

        return ds;
    }

    private void ensureDirectoryExists(Path path) {
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create directory: " + path, e);
            }
        }
    }
}

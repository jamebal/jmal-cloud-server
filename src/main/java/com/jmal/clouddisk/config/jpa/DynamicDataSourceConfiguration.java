package com.jmal.clouddisk.config.jpa;

import com.jmal.clouddisk.config.FileProperties;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Conditional(RelationalDataSourceCondition.class)
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
@Slf4j
public class DynamicDataSourceConfiguration {

    private final Environment environment;
    private final FileProperties fileProperties;

    @Bean
    public DataSource dataSource() {
        String driverClassName = environment.getProperty("spring.datasource.driver-class-name");
        if (DataSourceBeanPostProcessor.SQLITE_DRIVE_CLASS_NAME.equals(driverClassName)) {
            return createSqliteDataSource();
        } else {
            return DataSourceBuilder.create()
                    .type(HikariDataSource.class)
                    .url(environment.getProperty("spring.datasource.url"))
                    .driverClassName(driverClassName)
                    .username(environment.getProperty("spring.datasource.username"))
                    .password(environment.getProperty("spring.datasource.password"))
                    .build();
        }
    }

    private DataSource createSqliteDataSource() {
        Path dbPath = Paths.get(fileProperties.getRootDir(), fileProperties.getJmalcloudDBDir(), "db");
        ensureDirectoryExists(dbPath);
        Path dbDataPath = Paths.get(fileProperties.getRootDir(), fileProperties.getJmalcloudDBDir(), "data");
        ensureDirectoryExists(dbDataPath);
        String url = "jdbc:sqlite:" + dbPath.resolve("jmalcloud.db").toAbsolutePath();

        SQLiteConfig config = new SQLiteConfig();
        config.setJournalMode(SQLiteConfig.JournalMode.WAL);
        config.setBusyTimeout(10000);
        config.setLockingMode(SQLiteConfig.LockingMode.NORMAL);
        config.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);

        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl(url);
        dataSource.setConfig(config);

        return dataSource;
    }

    private void ensureDirectoryExists(Path path) {
        if (!Files.exists(path)) {
            try {
                log.info("Creating directory: {}", path.toAbsolutePath());
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create directory: " + path, e);
            }
        }
    }
}

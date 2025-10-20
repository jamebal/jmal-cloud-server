package com.jmal.clouddisk.config.jpa;


import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.flyway.FlywayProperties;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@Slf4j
@Component
public class DataSourceBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {

    public static final String DRIVE_CLASS_NAME_PROPERTIES = "spring.datasource.driver-class-name";
    public static final String DRIVE_URL = "spring.datasource.url";

    public static final String DATA_SOURCE_BEAN_NAME = "dataSource";

    public static final String SQLITE_DRIVE_CLASS_NAME = "org.sqlite.JDBC";

    public static final String MYSQL_DRIVE_CLASS_NAME = "com.mysql.cj.jdbc.Driver";

    public static final String POSTGRESQL_DRIVE_CLASS_NAME = "org.postgresql.Driver";

    @Override
    public Object postProcessBeforeInitialization(@NotNull Object bean, @NotNull String beanName) throws BeansException {
        // 如果更改了数据源类型这里要修改
        if (bean instanceof HikariDataSource dataSource && DATA_SOURCE_BEAN_NAME.equals(beanName)) {
            processSqliteDataSource(dataSource);
        } else if (bean instanceof FlywayProperties flywayProperties) {
            processFlywayLocations(flywayProperties);
        }
        return bean;
    }

    private void processSqliteDataSource(HikariDataSource dataSource) {
        String driverClassName = dataSource.getDriverClassName();
        if (CharSequenceUtil.equals(driverClassName, SQLITE_DRIVE_CLASS_NAME)) {
            log.info("Sqlite 数据库驱动类名: [{}]", driverClassName);
            FileProperties fileProperties = SpringUtil.getBean(FileProperties.class);
            Path dbPath = Paths.get(fileProperties.getRootDir(), fileProperties.getJmalcloudDBDir(), "db");
            ensureDirectoryExists(dbPath);
            Path dbDataPath = Paths.get(fileProperties.getRootDir(), fileProperties.getJmalcloudDBDir(), "data");
            ensureDirectoryExists(dbDataPath);
        }
    }

    private void processFlywayLocations(FlywayProperties flywayProperties) {
        String driveClassName = SpringUtil.getProperty(DRIVE_CLASS_NAME_PROPERTIES);
        log.info("当前数据源驱动类名: [{}]", driveClassName);
        log.info("当前数据源驱动URL: [{}]", SpringUtil.getProperty(DRIVE_URL));
        if (SQLITE_DRIVE_CLASS_NAME.equals(driveClassName)) {
            flywayProperties.setLocations(List.of("classpath:db/migration/sqlite"));
        } else if (MYSQL_DRIVE_CLASS_NAME.equals(driveClassName)) {
            flywayProperties.setLocations(List.of("classpath:db/migration/mysql"));
        } else if (POSTGRESQL_DRIVE_CLASS_NAME.equals(driveClassName)) {
            flywayProperties.setLocations(List.of("classpath:db/migration/postgresql"));
        }
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

    @Override
    public int getOrder() {
        return Integer.MIN_VALUE + 1;
    }

}

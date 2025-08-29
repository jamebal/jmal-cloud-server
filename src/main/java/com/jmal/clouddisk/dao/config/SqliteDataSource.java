package com.jmal.clouddisk.dao.config;

import cn.hutool.core.io.FileUtil;
import com.jmal.clouddisk.config.FileProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
        Path dbpath = Paths.get(fileProperties.getRootDir(), fileProperties.getJmalcloudDBDir(), "db");
        if (!FileUtil.exists(dbpath, true)) {
            try {
                Files.createDirectories(dbpath);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        Path dbDataPath = Paths.get(fileProperties.getRootDir(), fileProperties.getJmalcloudDBDir(), "data");
        if (!FileUtil.exists(dbDataPath, true)) {
            try {
                Files.createDirectories(dbDataPath);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        String url = String.format("jdbc:sqlite:%s/jmalcloud.db", dbpath);
        org.sqlite.SQLiteDataSource ds = new org.sqlite.SQLiteDataSource();
        ds.setUrl(url);
        return ds;
    }
}

package com.jmal.clouddisk.dao.migrate;

public interface IMigrationService {
    MigrationResult migrateData();

    String getName();
}

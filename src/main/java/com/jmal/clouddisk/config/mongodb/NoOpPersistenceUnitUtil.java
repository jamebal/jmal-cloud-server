package com.jmal.clouddisk.config.mongodb;

import jakarta.persistence.PersistenceUnitUtil;

public class NoOpPersistenceUnitUtil implements PersistenceUnitUtil {
    @Override
    public boolean isLoaded(Object o, String s) {
        return false;
    }

    @Override
    public boolean isLoaded(Object o) {
        return false;
    }

    @Override
    public Object getIdentifier(Object o) {
        return null;
    }
}

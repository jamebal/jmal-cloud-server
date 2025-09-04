package com.jmal.clouddisk.dao;

public interface ITrashDAO {
    long getOccupiedSpace(String userId, String collectionName);
}

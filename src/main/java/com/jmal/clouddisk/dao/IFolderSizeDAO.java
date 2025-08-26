package com.jmal.clouddisk.dao;

import com.jmal.clouddisk.model.file.FileDocument;

import java.util.List;

public interface IFolderSizeDAO {
    /**
     * 查询所有需要更新大小的文件夹
     * @param batchSize 每次查询的数量
     * @return 需要更新大小的文件夹列表
     */
    List<FileDocument> findFoldersNeedUpdateSize(int batchSize);

    /**
     * 更新文件大小
     * @param fileId 文件ID
     * @param size 文件大小
     */
    void updateFileSize(String fileId, long size);

    /**
     * 检查数据库中是否还有需要更新siz的文件夹
     * @return true表示有，false表示没有
     */
    boolean hasNeedUpdateSizeInDb();

    /**
     * 统计数据库中需要更新大小的文件夹数量
     * @return 数量
     */
    long totalSizeNeedUpdateSizeInDb();

    /**
     * 清空数据库中所有文件夹的大小信息
     */
    void clearFolderSizInDb();
}

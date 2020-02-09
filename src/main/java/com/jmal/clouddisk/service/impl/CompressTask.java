package com.jmal.clouddisk.service.impl;

import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.util.CompressUtils;

import java.io.IOException;
import java.util.List;

/**
 * @Description 压缩任务
 * @Author jmal
 * @Date 2020-02-06 17:28
 * @blame jmal
 */
public class CompressTask implements Runnable{

    private String startPath;
    private List<FileDocument> fileDocuments;
    private String targetFile;

    CompressTask(String startPath, List<FileDocument> fileDocuments, String targetFile){
        this.startPath = startPath;
        this.fileDocuments = fileDocuments;
        this.targetFile = targetFile;
    }

    @Override
    public void run() {
        try {
            System.out.println("开始压缩");
            long s = System.currentTimeMillis();
            CompressUtils.zip(startPath, fileDocuments, targetFile);
            System.out.println("耗时:"+(System.currentTimeMillis() - s)+"ms");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

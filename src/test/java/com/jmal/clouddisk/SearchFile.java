package com.jmal.clouddisk;

import com.jmal.clouddisk.util.FileFinderUtil;

import java.io.File;
import java.util.List;

/**
 * 实现一个支持通配符的基于广度优先算法的文件查找器
 */
class FileFinder {

    public static void main(String[] paramert) {
        long stime = System.currentTimeMillis();
        //	在此目录中找文件
        String baseDIR = "/Users/jmal/temp";
        //	找扩展名为txt的文件
        String fileName = "**";
        //	最多返回5个文件
        int countNumber = 5;
        List<File> resultList = FileFinderUtil.findFiles(baseDIR,"audio", fileName, countNumber);
        if (resultList.size() == 0) {
            System.out.println("No File Fount.");
        } else {
            for (int i = 0; i < resultList.size(); i++) {
                System.out.println(resultList.get(i));//显示查找结果。
            }
        }
        System.out.println(System.currentTimeMillis()-stime);
    }
}



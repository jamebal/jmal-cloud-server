package com.jmal.clouddisk.util;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;


import javax.activation.MimetypesFileTypeMap;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * @Description 文件查找器
 * @Author jmal
 * @Date 2020-03-01 13:06
 * @author jmal
 */
public class FileFinderUtil {
    /**
     * 查找文件。
     *
     * 算法简述：
     * 从某个给定的需查找的文件夹出发，搜索该文件夹的所有子文件夹及文件，
     * 若为文件，则进行匹配，匹配成功则加入结果集，若为子文件夹，则进队列。
     * 队列不空，重复上述操作，队列为空，程序结束，返回结果。
     *
     * @param baseDirName		待查找的目录
     * @param targetFileName	目标文件名，支持通配符形式
     * @param count				期望结果数目，如果畏0，则表示查找全部。
     * @return		满足查询条件的文件名列表
     */
    public static List<File> findFiles(String baseDirName,String type, String targetFileName, int count) {
        MimetypesFileTypeMap mimetypesFileTypeMap =  new MimetypesFileTypeMap();
        List<File> fileList = new ArrayList<>();
        //判断目录是否存在
        File baseDir = new File(baseDirName);
        if (!baseDir.exists() || !baseDir.isDirectory()){
            System.out.println("文件查找失败：" + baseDirName + "不是一个目录！");
            return fileList;
        }
        String tempName;
        BlockingDeque<File> queue = new LinkedBlockingDeque<>();
        //入队
        queue.add(baseDir);
        File tempFile;
        while (!queue.isEmpty()) {
            //从队列中取目录
            tempFile = queue.pop();
            if (tempFile.exists() && tempFile.isDirectory()) {
                File[] files = tempFile.listFiles(pathname -> {
                    if(StrUtil.isBlank(type)){
                        return true;
                    }
                    if(pathname.isFile()){
                        String contentType = FileContentTypeUtils.getContentType(FileUtil.extName(pathname));
                        if(contentType.contains(type)){
                            return true;
                        }
                        return false;
                    }else{
                        return true;
                    }
                });
                assert files != null;
                for (File file : files) {
                    //如果是目录则放进队列
                    if (file.isDirectory()) {
                        queue.add(file);
                        if(!StrUtil.isBlank(type)){
                            continue;
                        }
                    }
                    tempName = file.getName();
                    if (wildcardMatch(targetFileName, tempName)) {
                        //匹配成功，将文件名添加到结果集
                        fileList.add(file.getAbsoluteFile());
                        //如果已经达到指定的数目，则退出循环
                        if ((count != 0) && (fileList.size() >= count)) {
                            return fileList;
                        }
                    }
                }
            }
        }

        return fileList;
    }
    /**
     * 通配符匹配
     * @param pattern	通配符模式
     * @param str	待匹配的字符串
     * @return	匹配成功则返回true，否则返回false
     */
    private static boolean wildcardMatch(String pattern, String str) {
        int patternLength = pattern.length();
        int strLength = str.length();
        int strIndex = 0;
        char ch;
        for (int patternIndex = 0; patternIndex < patternLength; patternIndex++) {
            ch = pattern.charAt(patternIndex);
            if (ch == '*') {
                //通配符星号*表示可以匹配任意多个字符
                while (strIndex < strLength) {
                    if (wildcardMatch(pattern.substring(patternIndex + 1),
                            str.substring(strIndex))) {
                        return true;
                    }
                    strIndex++;
                }
            } else if (ch == '?') {
                //通配符问号?表示匹配任意一个字符
                strIndex++;
                if (strIndex > strLength) {
                    //表示str中已经没有字符匹配?了。
                    return false;
                }
            } else {
                if ((strIndex >= strLength) || (ch != str.charAt(strIndex))) {
                    return false;
                }
                strIndex++;
            }
        }
        return (strIndex == strLength);
    }
}

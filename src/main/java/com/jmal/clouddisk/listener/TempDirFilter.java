package com.jmal.clouddisk.listener;

import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import java.io.File;
import java.io.FileFilter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * @Description 过滤掉不需要监听的目录
 * @Author jmal
 * @Date 2020-03-24 10:02
 */
public class TempDirFilter implements FileFilter {

    private String rootPath;
    private String[] filterDirPath;

    public TempDirFilter(String rootPath, String... filterDirPath){
        this.rootPath = rootPath;
        this.filterDirPath = filterDirPath;
    }

    @Override
    public boolean accept(File pathname) {
        for (String dirPath : filterDirPath) {
            if(pathname.toPath().startsWith(Paths.get(rootPath,dirPath))){
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) throws Exception {
        String rootDir = "/Users/jmal/temp/plugins";
        // 轮询间隔 1 秒
        long interval = TimeUnit.SECONDS.toMillis(1);
        // 创建过滤器
        TempDirFilter tempDirFilter = new TempDirFilter(rootDir,"temporary directory");

        // 使用过滤器
        FileAlterationObserver observer = new FileAlterationObserver(new File(rootDir), tempDirFilter);
        // 不使用过滤器
//        FileAlterationObserver observer = new FileAlterationObserver(new File(rootDir));
        observer.addListener(new FileListener());
        //创建文件变化监听器
        FileAlterationMonitor monitor = new FileAlterationMonitor(interval, observer);
        // 开始监控
        monitor.start();
    }

}

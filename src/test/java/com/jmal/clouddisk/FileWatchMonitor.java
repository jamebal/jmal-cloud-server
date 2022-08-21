package com.jmal.clouddisk;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.watch.SimpleWatcher;
import cn.hutool.core.io.watch.WatchMonitor;
import cn.hutool.core.lang.Console;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * @Description 文件监听
 * @Author jmal
 * @Date 2020/9/29 5:44 下午
 */
public class FileWatchMonitor {

    public static void main(String[] args) {
        File file = FileUtil.file("/Users/jmal/temp/filetest/rootpath");
        WatchMonitor watchMonitor = WatchMonitor.createAll(file, new MySimpleWatcher());
        watchMonitor.setMaxDepth(16);
        //启动监听
        watchMonitor.start();
        Console.log("启动监听");
    }

    private static class MySimpleWatcher extends SimpleWatcher{
        @Override
        public void onCreate(WatchEvent<?> event, Path currentPath) {
            Console.log("创建：{}-> {}", currentPath, event);
        }

        @Override
        public void onModify(WatchEvent<?> event, Path currentPath) {
            Console.log("修改：{}-> {}", currentPath, event);
        }

        @Override
        public void onDelete(WatchEvent<?> event, Path currentPath) {
            Console.log("删除：{}-> {}", currentPath, event);
        }

        @Override
        public void onOverflow(WatchEvent<?> event, Path currentPath) {
            Console.log("Overflow：{}-> {}", currentPath, event);
        }
    }
}

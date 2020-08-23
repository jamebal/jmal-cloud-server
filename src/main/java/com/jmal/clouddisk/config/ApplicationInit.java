package com.jmal.clouddisk.config;

import com.jmal.clouddisk.listener.FileListener;
import com.jmal.clouddisk.listener.TempDirFilter;
import com.jmal.clouddisk.model.FilePropertie;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * @Description 启动时开启文件目录监控
 * @Date 2020-02-21 14:54
 * @blame jmal
 */
@Component
@Slf4j
public class ApplicationInit implements ApplicationRunner {

    @Autowired
    FilePropertie filePropertie;

    @Autowired
    FileListener fileListener;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        // 判断是否开启文件监控
        if (!filePropertie.getMonitor()) return;
        Path rootDir = Paths.get(filePropertie.getRootDir());
        if (!Files.exists(rootDir)) {
            Files.createDirectories(rootDir);
        }
        // 轮询间隔(秒)
        long interval = TimeUnit.SECONDS.toMillis(filePropertie.getTimeInterval());
        // 创建过滤器
        TempDirFilter tempDirFilter = new TempDirFilter(filePropertie.getRootDir(), filePropertie.getChunkFileDir());

        // 使用过滤器
        FileAlterationObserver observer = new FileAlterationObserver(new File(filePropertie.getRootDir()), tempDirFilter);
        // 不使用过滤器
//        FileAlterationObserver observer = new FileAlterationObserver(new File(rootDir));
        observer.addListener(fileListener);
        //创建文件变化监听器
        FileAlterationMonitor monitor = new FileAlterationMonitor(interval, observer);
        // 开始监控
        monitor.start();
        log.info("\r\n文件监控服务已开启:\r\n轮询间隔:{}秒\n监控目录:{}\n忽略目录:{}", filePropertie.getTimeInterval(), rootDir, rootDir.toString() + File.separator + filePropertie.getChunkFileDir());
    }
}

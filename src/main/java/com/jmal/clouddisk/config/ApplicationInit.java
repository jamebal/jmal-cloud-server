package com.jmal.clouddisk.config;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.jmal.clouddisk.listener.FileListener;
import com.jmal.clouddisk.listener.TempDirFilter;
import com.jmal.clouddisk.model.FilePropertie;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * @Description 启动项目时创建mongodb索引
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
        Path rootDir = Paths.get(filePropertie.getRootDir());
        if(!Files.exists(rootDir)){
            Files.createDirectories(rootDir);
        }
        // 轮询间隔(秒)
        long interval = TimeUnit.SECONDS.toMillis(filePropertie.getTimeInterval());
        // 创建过滤器
        TempDirFilter tempDirFilter = new TempDirFilter(filePropertie.getRootDir(),filePropertie.getChunkFileDir());

        // 使用过滤器
        FileAlterationObserver observer = new FileAlterationObserver(new File(filePropertie.getRootDir()), tempDirFilter);
        // 不使用过滤器
//        FileAlterationObserver observer = new FileAlterationObserver(new File(rootDir));
        observer.addListener(fileListener);
        //创建文件变化监听器
        FileAlterationMonitor monitor = new FileAlterationMonitor(interval, observer);
        // 开始监控
        monitor.start();
        log.info("文件监控服务已开启,轮询间隔:{}秒, 监控目录:{},忽略目录:{}",filePropertie.getTimeInterval(),rootDir,rootDir.toString() + File.separator + filePropertie.getChunkFileDir());
    }
}

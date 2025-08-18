package com.jmal.clouddisk.ftp;

import cn.hutool.extra.ftp.SimpleFtpServer;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.rbac.ConsumerDTO;
import com.jmal.clouddisk.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author jmal
 * @Description FTPServer 配置
 * @date 2023/2/21 17:17
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FTPServerConfig  implements ApplicationListener<ContextRefreshedEvent> {

    private final FileProperties fileProperties;

    private final IUserService userService;

    private final MyPropertiesUserManager myPropertiesUserManager;

    public void startFTPServer() {

        List<ConsumerDTO> userList = userService.userListAll();

        SimpleFtpServer ftpServer = SimpleFtpServer.create();
        ftpServer.setUserManager(myPropertiesUserManager);
        ftpServer.setPort(fileProperties.getFtpServerPort());

        userList.forEach(consumerDTO -> {
            BaseUser user = new BaseUser();
            user.setName(consumerDTO.getUsername());
            Path path = Paths.get(fileProperties.getRootDir(), consumerDTO.getUsername());
            user.setHomeDirectory(path.toString());
            ftpServer.addUser(user);
        });
        ftpServer.start();
        log.info("FTP server port: {}", fileProperties.getFtpServerPort());
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        // 确保只在根应用上下文执行一次，防止在Web环境中执行两次
        if (event.getApplicationContext().getParent() == null) {
            startFTPServer();
        }
    }
}

package com.jmal.clouddisk.ftp;

import cn.hutool.extra.ftp.SimpleFtpServer;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.rbac.ConsumerDTO;
import com.jmal.clouddisk.service.IUserService;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * @author jmal
 * @Description FTPServer 配置
 * @date 2023/2/21 17:17
 */
@Component
public class FTPServerConfig {

    private final FileProperties fileProperties;

    private final IUserService userService;

    public FTPServerConfig(FileProperties fileProperties, IUserService userService) {
        this.fileProperties = fileProperties;
        this.userService = userService;
    }

    @PostConstruct
    public void startFTPServer () {

        List<ConsumerDTO> userList = userService.userListAll();

        SimpleFtpServer ftpServer = SimpleFtpServer.create();

        userList.forEach(consumerDTO -> {
            BaseUser user = new BaseUser();
            user.setName(consumerDTO.getUsername());
            user.setPassword(consumerDTO.getPassword());
            Path path = Paths.get(fileProperties.getRootDir(), consumerDTO.getUsername());
            user.setHomeDirectory(path.toString());
            ftpServer.addUser(user);
        });
        ftpServer.start();
    }

}

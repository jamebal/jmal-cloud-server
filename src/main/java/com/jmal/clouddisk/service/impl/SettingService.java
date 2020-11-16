package com.jmal.clouddisk.service.impl;

import cn.hutool.core.util.ArrayUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.model.FileProperties;
import com.jmal.clouddisk.model.UserSetting;
import com.jmal.clouddisk.model.UserSettingDTO;
import com.jmal.clouddisk.repository.IAuthDAO;
import com.jmal.clouddisk.util.MongoUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jmal
 * @Description 设置
 * @Date 2020/10/28 5:30 下午
 */
@Service
@Slf4j
public class SettingService {

    @Autowired
    FileProperties fileProperties;

    @Autowired
    FileServiceImpl fileService;

    @Autowired
    private MongoTemplate mongoTemplate;

    private static final String COLLECTION_NAME_WEBSITE_SETTING = "websiteSetting";

    @Autowired
    private IAuthDAO authDAO;

    /***
     * 把文件同步到数据库
     * @param username 用户名
     */
    public void sync(String username) {
        Path path = Paths.get(fileProperties.getRootDir(),username);
        List<File> list = loopFiles(path.toFile());
        list.parallelStream().forEach(file -> fileService.createFile(username, file));
    }

    /***
     * 递归遍历目录以及子目录中的所有文件
     * @param file 当前遍历文件
     * @return 文件列表
     */
    public static List<File> loopFiles(File file) {
        final List<File> fileList = new ArrayList<>();
        if (null == file || !file.exists()) {
            return fileList;
        }
        fileList.add(file);
        if (file.isDirectory()) {
            final File[] subFiles = file.listFiles();
            if (ArrayUtil.isNotEmpty(subFiles)) {
                for (File tmp : subFiles) {
                    fileList.addAll(loopFiles(tmp));
                }
            }
        }
        return fileList;
    }

    /***
     * 更新用户设置
     * @param userSetting UserSetting
     * @return ResponseResult
     */
    public ResponseResult<Object> update(UserSetting userSetting) {
        Query query = new Query();
        Update update = MongoUtil.getUpdate(userSetting);
        mongoTemplate.upsert(query, update, COLLECTION_NAME_WEBSITE_SETTING);
        return ResultUtil.success();
    }

    /***
     * 获取网站设置
     * @return ResponseResult
     */
    public UserSettingDTO getWebsiteSetting() {
        UserSettingDTO userSettingDTO = new UserSettingDTO();
        Query query = new Query();
        UserSetting userSetting = mongoTemplate.findOne(query, UserSetting.class, COLLECTION_NAME_WEBSITE_SETTING);
        if(userSetting != null){
            CglibUtil.copy(userSetting, userSettingDTO);
        }
        return userSettingDTO;
    }

    /***
     * 生成accessToken
     * @param username 用户名
     * @return ResponseResult
     */
    public ResponseResult<String> generateAccessToken(String username) {
        byte[] key = SecureUtil.generateKey(SymmetricAlgorithm.AES.getValue()).getEncoded();
        // 构建
        AES aes = SecureUtil.aes(key);
        // 加密为16进制表示
        String accessToken = aes.encryptHex(username);
        authDAO.upsertAccessToken(username, accessToken);
        return ResultUtil.success(accessToken);
    }

}

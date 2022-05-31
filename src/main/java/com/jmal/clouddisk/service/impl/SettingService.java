package com.jmal.clouddisk.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.exception.CommonException;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.repository.IAuthDAO;
import com.jmal.clouddisk.util.MongoUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.mime.MimeType;
import org.apache.tika.mime.MimeTypeException;
import org.apache.tika.mime.MimeTypes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;


import javax.annotation.PostConstruct;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
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

    @Autowired
    private MenuService menuService;

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private RoleService roleService;

    @Autowired
    UserLoginHolder userLoginHolder;

    @PostConstruct
    public void init(){
        // 启动时检测是否存在菜单，不存在则初始化
        if(!menuService.existsMenu()){
            log.info("初始化角色、菜单！");
            menuService.initMenus();
            roleService.initRoles();
        }
    }

    /***
     * 把文件同步到数据库
     * @param username 用户名
     */
    public void sync(String username) {
        Path path = Paths.get(fileProperties.getRootDir(), username);
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
     * 更新网站设置
     * @param websiteSettingDO WebsiteSetting
     * @return ResponseResult
     */
    public ResponseResult<Object> websiteUpdate(WebsiteSettingDO websiteSettingDO) {
        Query query = new Query();
        Update update = MongoUtil.getUpdate(websiteSettingDO);
        // 添加心语记录
        addHeartwings(websiteSettingDO);
        mongoTemplate.upsert(query, update, COLLECTION_NAME_WEBSITE_SETTING);
        return ResultUtil.success();
    }

    /***
     * 添加心语记录
     * @param websiteSettingDO WebsiteSettingDO
     */
    private void addHeartwings(WebsiteSettingDO websiteSettingDO) {
        WebsiteSettingDO websiteSettingDO1 = mongoTemplate.findOne(new Query(), WebsiteSettingDO.class, COLLECTION_NAME_WEBSITE_SETTING);
        if (websiteSettingDO1 != null){
            String oldHeartwings = websiteSettingDO1.getBackgroundTextSite();
            String heartwings = websiteSettingDO.getBackgroundTextSite();
            if (!StrUtil.isBlank(oldHeartwings) && !oldHeartwings.equals(heartwings)) {
                HeartwingsDO heartwingsDO = new HeartwingsDO();
                heartwingsDO.setCreateTime(LocalDateTimeUtil.now());
                heartwingsDO.setCreator(userLoginHolder.getUserId());
                heartwingsDO.setUsername(userLoginHolder.getUsername());
                heartwingsDO.setHeartwings(heartwings);
                mongoTemplate.save(heartwingsDO);
            }
        }
    }

    /**
     * 获取网站备案信息
     *
     * @return WebsiteSettingDTO
     */
    public WebsiteSettingDTO getWebsiteRecord() {
        WebsiteSettingDTO websiteSettingDTO = getWebsiteSetting();
        WebsiteSettingDTO websiteSettingDTO1 = new WebsiteSettingDTO();
        websiteSettingDTO1.setCopyright(websiteSettingDTO.getCopyright());
        websiteSettingDTO1.setRecordPermissionNum(websiteSettingDTO.getRecordPermissionNum());
        return websiteSettingDTO1;
    }

    /***
     * 获取网站设置
     * @return ResponseResult
     */
    public WebsiteSettingDTO getWebsiteSetting() {
        WebsiteSettingDTO websiteSettingDTO = new WebsiteSettingDTO();
        Query query = new Query();
        WebsiteSettingDO websiteSettingDO = mongoTemplate.findOne(query, WebsiteSettingDO.class, COLLECTION_NAME_WEBSITE_SETTING);
        if(websiteSettingDO != null){
            CglibUtil.copy(websiteSettingDO, websiteSettingDTO);
        }
        if(websiteSettingDTO.getAlonePages() == null){
            websiteSettingDTO.setAlonePages(new ArrayList<>());
        }
        String avatar = userService.getCreatorAvatar();
        if(!StrUtil.isBlank(avatar)){
            websiteSettingDTO.setAvatar(avatar);
        }
        return websiteSettingDTO;
    }

    public ResponseResult<List<HeartwingsDO>> getWebsiteHeartwings(Integer page, Integer pageSize, String order) {
        Query query = new Query();
        long count = mongoTemplate.count(query, HeartwingsDO.class);
        query.skip((long) pageSize * (page - 1));
        query.limit(pageSize);
        Sort.Direction direction = Sort.Direction.ASC;
        if ("descending".equals(order)) {
            direction = Sort.Direction.DESC;
        }
        query.with(Sort.by(direction, "createTime"));
        return ResultUtil.success(mongoTemplate.find(query, HeartwingsDO.class)).setCount(count);
    }

    /***
     * 生成accessToken
     * @param username 用户名
     * @param tokenName tokenName
     * @return ResponseResult
     */
    public ResponseResult<String> generateAccessToken(String username, String tokenName) {
        byte[] key = SecureUtil.generateKey(SymmetricAlgorithm.AES.getValue()).getEncoded();
        // 构建
        AES aes = SecureUtil.aes(key);
        // 加密为16进制表示
        String accessToken = aes.encryptHex(username);
        UserAccessTokenDO userAccessTokenDO = new UserAccessTokenDO();
        userAccessTokenDO.setName(tokenName);
        userAccessTokenDO.setUsername(username);
        userAccessTokenDO.setAccessToken(accessToken);
        authDAO.generateAccessToken(userAccessTokenDO);
        return ResultUtil.success(accessToken);
    }

    /***
     * accessToken列表
     * @param username 用户名
     * @return List<UserAccessTokenDTO>
     */
    public ResponseResult<List<UserAccessTokenDTO>> accessTokenList(String username) {
        List<UserAccessTokenDTO> list = authDAO.accessTokenList(username);
        return ResultUtil.success(list);
    }

    /***
     * 删除accessToken
     * @param id accessTokenId
     */
    public void deleteAccessToken(String id) {
        authDAO.deleteAccessToken(id);
    }

    public void resetMenuAndRole() {
        menuService.initMenus();
        roleService.initRoles();
    }

    // /**
    //  * 更新网盘设置
    //  * @param cloudSettingDTO 网盘设置DTO
    //  */
    // public ResponseResult<Object> cloudUpdate(CloudSettingDTO cloudSettingDTO) {
    //     MultipartFile multipartFile = cloudSettingDTO.getFile();
    //     String fileName = "cloud-logo";
    //     MimeTypes allTypes = MimeTypes.getDefaultMimeTypes();
    //     MimeType mimeType = null;
    //     try {
    //         mimeType = allTypes.forName(multipartFile.getContentType());
    //         fileName += mimeType.getExtension();
    //     } catch (MimeTypeException e) {
    //         log.error(e.getMessage(), e);
    //     }
    //     Path userImagePaths = Paths.get(fileProperties.getUserImgDir());
    //     // userImagePaths 不存在则新建
    //     upsertFolder(userImagePaths, username, userId);
    //     File newFile;
    //     try {
    //         if (userService.getDisabledWebp(userId) || ("ico".equals(FileUtil.getSuffix(fileName)))) {
    //             newFile = Paths.get(fileProperties.getRootDir(), username, userImagePaths.toString(), fileName).toFile();
    //             FileUtil.writeFromStream(multipartFile.getInputStream(), newFile);
    //         } else {
    //             fileName = fileName + _SUFFIX_WEBP;
    //             newFile = Paths.get(fileProperties.getRootDir(), username, userImagePaths.toString(), fileName).toFile();
    //             BufferedImage image = ImageIO.read(multipartFile.getInputStream());
    //             imageFileToWebp(newFile, image);
    //         }
    //     } catch (IOException e) {
    //         throw new CommonException(2, "上传失败");
    //     }
    //     return createFile(username, newFile, userId, true);
    // }
}

package com.jmal.clouddisk.service.impl;

import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import cn.hutool.extra.cglib.CglibUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.repository.IAuthDAO;
import com.jmal.clouddisk.util.MongoUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    private final Set<String> syncCache = new ConcurrentHashSet<>(1);

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
    public ResponseResult<Object> sync(String username) {
        if (syncCache.isEmpty()) {
            syncCache.add("syncing");
            log.info("开始同步");
            ThreadUtil.execute(() -> {
                Path path = Paths.get(fileProperties.getRootDir(), username);
                try {
                    Files.walkFileTree(path, new SyncFileVisitor(username));
                } catch (IOException e) {
                    log.error(e.getMessage() + path, e);
                } finally {
                    syncCache.clear();
                    log.info("同步完成");
                    fileService.pushMessage(username, null, "synced");
                }
            });
        }
        return ResultUtil.success();
    }

    /**
     * 是否正在同步中
     */
    public ResponseResult<Object> isSync() {
        if (syncCache.isEmpty()) {
            return ResultUtil.success(false);
        }
        return ResultUtil.success(true);
    }

    /**
     * 上传网盘logo
     * @param file logo文件
     */
    public ResponseResult<Object> uploadLogo(MultipartFile file) {
        String filename = "logo-" + System.currentTimeMillis() + "." + FileUtil.extName(file.getOriginalFilename());
        File dist = new File(fileProperties.getRootDir() + File.separator + filename);
        try {
            String oldFilename = null;
            Query query = new Query();
            WebsiteSettingDO websiteSettingDO = mongoTemplate.findOne(query, WebsiteSettingDO.class, COLLECTION_NAME_WEBSITE_SETTING);
            if (websiteSettingDO != null){
                oldFilename = websiteSettingDO.getNetdiskLogo();
            }
            // 保存新的logo文件
            FileUtil.writeFromStream(file.getInputStream(), dist);
            Update update = new Update();
            update.set("netdiskLogo", filename);
            mongoTemplate.upsert(new Query(), update, COLLECTION_NAME_WEBSITE_SETTING);
            if (!CharSequenceUtil.isBlank(oldFilename)) {
                // 删除之前的logo文件
                PathUtil.del(Paths.get(fileProperties.getRootDir(), oldFilename));
            }
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return ResultUtil.error("上传网盘logo失败");
        }
        return ResultUtil.success(filename);
    }

    /**
     * 修改网盘名称
     * @param netdiskName 网盘名称
     */
    public ResponseResult<Object> updateNetdiskName(String netdiskName) {
        Query query = new Query();
        Update update = new Update();
        update.set("netdiskName", netdiskName);
        mongoTemplate.upsert(query, update, COLLECTION_NAME_WEBSITE_SETTING);
        return ResultUtil.success("修改成功");
    }

    private class SyncFileVisitor extends SimpleFileVisitor<Path> {

        private final String username;

        public SyncFileVisitor(String username) {
            this.username = username;
        }

        @Override
        public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
            log.error(exc.getMessage(), exc);
            return super.visitFileFailed(file, exc);
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
            try{
                fileService.createFile(username, dir.toFile());
            } catch (Exception e) {
                log.error(e.getMessage() + dir, e);
            }
            return super.preVisitDirectory(dir, attrs);
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            try{
                fileService.createFile(username, file.toFile());
            } catch (Exception e) {
                log.error(e.getMessage() + file, e);
            }
            return super.visitFile(file, attrs);
        }

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
            if (!CharSequenceUtil.isBlank(oldHeartwings) && !oldHeartwings.equals(heartwings)) {
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
        websiteSettingDTO1.setNetdiskName(websiteSettingDTO.getNetdiskName());
        websiteSettingDTO1.setNetdiskLogo(websiteSettingDTO.getNetdiskLogo());
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
        if(!CharSequenceUtil.isBlank(avatar)){
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

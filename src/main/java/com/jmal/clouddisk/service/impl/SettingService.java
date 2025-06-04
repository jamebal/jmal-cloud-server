package com.jmal.clouddisk.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.repository.IAuthDAO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.util.MongoUtil;
import com.jmal.clouddisk.util.MyFileUtils;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
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
    private MongoTemplate mongoTemplate;

    protected static final String COLLECTION_NAME_WEBSITE_SETTING = "websiteSetting";

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

    /**
     * 上传网盘logo
     *
     * @param file logo文件
     */
    public ResponseResult<Object> uploadLogo(MultipartFile file) {
        String filename = "logo-" + System.currentTimeMillis() + "." + MyFileUtils.extName(file.getOriginalFilename());
        File dist = new File(fileProperties.getRootDir() + File.separator + filename);
        try {
            String oldFilename = null;
            Query query = new Query();
            WebsiteSettingDO websiteSettingDO = mongoTemplate.findOne(query, WebsiteSettingDO.class, COLLECTION_NAME_WEBSITE_SETTING);
            if (websiteSettingDO != null) {
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
     *
     * @param netdiskName 网盘名称
     */
    public ResponseResult<Object> updateNetdiskName(String netdiskName) {
        Query query = new Query();
        Update update = new Update();
        update.set("netdiskName", netdiskName);
        mongoTemplate.upsert(query, update, COLLECTION_NAME_WEBSITE_SETTING);
        return ResultUtil.success("修改成功");
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
        if (websiteSettingDO1 != null) {
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
        websiteSettingDTO1.setNetworkRecordNumber(websiteSettingDTO.getNetworkRecordNumber());
        websiteSettingDTO1.setNetworkRecordNumberStr(websiteSettingDTO.getNetworkRecordNumberStr());
        websiteSettingDTO1.setNetdiskName(websiteSettingDTO.getNetdiskName());
        websiteSettingDTO1.setExactSearch(fileProperties.getExactSearch());
        websiteSettingDTO1.setNetdiskLogo(websiteSettingDTO.getNetdiskLogo());
        websiteSettingDTO1.setFooterHtml(websiteSettingDTO.getFooterHtml());
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
        if (websiteSettingDO != null) {
            BeanUtils.copyProperties(websiteSettingDO, websiteSettingDTO);
        }
        if (websiteSettingDTO.getAlonePages() == null) {
            websiteSettingDTO.setAlonePages(new ArrayList<>());
        }
        String avatar = userService.getCreatorAvatar();
        if (!CharSequenceUtil.isBlank(avatar)) {
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
        query.with(Sort.by(direction, Constants.CREATE_TIME));
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

    public WebsiteSettingDTO getPreviewConfig() {
        Query query = new Query();
        query.fields().include("iframe");
        WebsiteSettingDTO websiteSettingDTO = mongoTemplate.findOne(new Query(), WebsiteSettingDTO.class, COLLECTION_NAME_WEBSITE_SETTING);
        if (websiteSettingDTO == null) {
            return new WebsiteSettingDTO();
        }
        return websiteSettingDTO;
    }

    public synchronized void updatePreviewConfig(WebsiteSettingDTO websiteSettingDTO) {
        Update update = new Update();
        update.set("iframe", websiteSettingDTO.getIframe());
        mongoTemplate.upsert(new Query(), update, COLLECTION_NAME_WEBSITE_SETTING);
    }
}

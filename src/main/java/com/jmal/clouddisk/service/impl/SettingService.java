package com.jmal.clouddisk.service.impl;

import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.file.PathUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.crypto.SecureUtil;
import cn.hutool.crypto.symmetric.AES;
import cn.hutool.crypto.symmetric.SymmetricAlgorithm;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.lucene.EtagService;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.repository.IAuthDAO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author jmal
 * @Description 设置
 * @Date 2020/10/28 5:30 下午
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SettingService {

    private final FileProperties fileProperties;

    private final MongoTemplate mongoTemplate;

    protected static final String COLLECTION_NAME_WEBSITE_SETTING = "websiteSetting";

    private final IAuthDAO authDAO;

    private final MenuService menuService;

    private final UserServiceImpl userService;

    private final RoleService roleService;

    private final UserLoginHolder userLoginHolder;

    private final EtagService etagService;

    private final MessageService messageService;

    private final AtomicBoolean calculateFolderSizeScheduled = new AtomicBoolean(false);

    /**
     * 已处理的数量
     */
    private final AtomicLong calculateFolderSizeProcessedCount = new AtomicLong(0);

    /**
     * 每次处理的延迟时间，单位毫秒
     */
    private final static long THROTTLE_DELAY_MS = 100;

    /**
     * 每次处理的文件夹数量
     */
    private final static int FOLDER_BATCH_SIZE = 16;

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

    /**
     * 重新计算文件夹大小
     * @return ResponseResult
     */
    public ResponseResult<Object> recalculateFolderSize() {
        if (calculateFolderSizeScheduled.get()) {
            return ResultUtil.success();
        } else {
            // 确保计算文件夹大小的任务被调度
            ensureProcessCalculateFolderSize();
        }
        return ResultUtil.success();
    }

    private void ensureProcessCalculateFolderSize() {
        if (calculateFolderSizeScheduled.compareAndSet(false, true)) {
            String notifyUsername = userLoginHolder.getUsername();
            CompletableFuture.runAsync(() -> {
                try {
                    // 清除之前的文件夹大小数据
                    clearFolderSizInDb();
                    long totalSize = totalSizeNeedUpdateSizeInDb();
                    calculateFolderSizeProcessedCount.set(0);
                    HybridThrottleExecutor hybridThrottleExecutor = new HybridThrottleExecutor(THROTTLE_DELAY_MS);
                    // 调用实际的循环处理逻辑
                    processCalculateFolderSizeLoop(totalSize, hybridThrottleExecutor, notifyUsername);
                    hybridThrottleExecutor.shutdown();
                } finally {
                    calculateFolderSizeScheduled.set(false);
                    // 关键：检查在本次处理运行期间是否有新的文件夹被标记
                    // 如果有，则再次尝试调度，确保它们得到处理
                    if (hasNeedUpdateSizeInDb()) {
                        ensureProcessCalculateFolderSize();
                    }
                }

            });
        }
    }

    private void processCalculateFolderSizeLoop(long totalSize, HybridThrottleExecutor hybridThrottleExecutor, String notifyUsername) {
        boolean run = true;
        while (run && !Thread.currentThread().isInterrupted()) {
            // 查询所有需要更新大小的文件夹
            Query query = Query.query(Criteria.where(Constants.IS_FOLDER).is(true).and(Constants.SIZE).exists(false)).limit(FOLDER_BATCH_SIZE);
            List<FileDocument> tasks = mongoTemplate.find(query, FileDocument.class);
            if (tasks.isEmpty()) {
                run = false;
                continue;
            }
            for (FileDocument folderDoc : tasks) {
                if (Thread.currentThread().isInterrupted()) {
                    run = false;
                    break;
                }
                String currentFolderNormalizedPath = folderDoc.getPath() + folderDoc.getName() + "/";
                try {
                    // 计算文件夹大小
                    long size = etagService.getFolderSize(CommonFileService.COLLECTION_NAME, folderDoc.getUserId(), currentFolderNormalizedPath);
                    // 更新数据库中的大小
                    Update update = new Update();
                    update.set(Constants.SIZE, size);
                    mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(folderDoc.getId())), update, FileDocument.class);
                    calculateFolderSizeProcessedCount.getAndIncrement();
                    // 推送进度
                    hybridThrottleExecutor.execute(() -> {
                        double progress = (totalSize > 0) ? (double) calculateFolderSizeProcessedCount.get() / totalSize * 100 : 100.0;
                        messageService.pushMessage(notifyUsername, NumberUtil.round(progress, 2), "calculateFolderSizeProcessed");
                    });

                } catch (Exception e) {
                    log.error("计算文件夹大小失败: {}", currentFolderNormalizedPath, e);
                }
            }
        }
    }

    /**
     * 检查数据库中是否还有需要更新siz的文件夹
     */
    private boolean hasNeedUpdateSizeInDb() {
        Query query = Query.query(Criteria.where(Constants.IS_FOLDER).is(true).and(Constants.SIZE).exists(false));
        query.limit(1); // 只需要知道是否存在，不需要完整计数
        return mongoTemplate.exists(query, FileDocument.class);
    }

    /**
     * 获取需要更新大小的文件夹总数
     * @return long
     */
    private long totalSizeNeedUpdateSizeInDb() {
        Query query = Query.query(Criteria.where(Constants.IS_FOLDER).is(true).and(Constants.SIZE).exists(false));
        return mongoTemplate.count(query, FileDocument.class);
    }

    /**
     * 清除数据库中所有文件夹的大小字段
     */
    private void clearFolderSizInDb() {
        Query query = Query.query(Criteria.where(Constants.IS_FOLDER).is(true));
        Update update = new Update();
        update.unset(Constants.SIZE);
        mongoTemplate.updateMulti(query, update, FileDocument.class);
    }

    public boolean getMfaForceEnable() {
        Query query = new Query();
        WebsiteSettingDO websiteSettingDO = mongoTemplate.findOne(query, WebsiteSettingDO.class, COLLECTION_NAME_WEBSITE_SETTING);
        if (websiteSettingDO != null) {
            return BooleanUtil.isTrue(websiteSettingDO.getMfaForceEnable());
        }
        return false;
    }

    public void setMfaForceEnable(Boolean mfaForceEnable) {
        Query query = new Query();
        Update update = new Update();
        update.set("mfaForceEnable", mfaForceEnable);
        mongoTemplate.upsert(query, update, COLLECTION_NAME_WEBSITE_SETTING);
    }
}

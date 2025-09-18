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
import com.jmal.clouddisk.dao.IAccessTokenDAO;
import com.jmal.clouddisk.dao.IFolderSizeDAO;
import com.jmal.clouddisk.dao.IHeartwingsDAO;
import com.jmal.clouddisk.dao.IWebsiteSettingDAO;
import com.jmal.clouddisk.lucene.EtagService;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.util.HybridThrottleExecutor;
import com.jmal.clouddisk.util.MyFileUtils;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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

    private final IWebsiteSettingDAO websiteSettingDAO;

    private final IHeartwingsDAO heartwingsDAO;

    private final IAccessTokenDAO accessTokenDAO;

    private final IFolderSizeDAO folderSizeDAO;

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
            WebsiteSettingDO websiteSettingDO = websiteSettingDAO.findOne();
            if (websiteSettingDO != null) {
                oldFilename = websiteSettingDO.getNetdiskLogo();
            }
            // 保存新的logo文件
            FileUtil.writeFromStream(file.getInputStream(), dist);
            websiteSettingDAO.updateLogo(filename);
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
        websiteSettingDAO.updateName(netdiskName);
        return ResultUtil.success("修改成功");
    }

    /***
     * 更新网站设置
     * @param websiteSettingDO WebsiteSetting
     * @return ResponseResult
     */
    public ResponseResult<Object> websiteUpdate(WebsiteSettingDO websiteSettingDO) {
        // 添加心语记录
        addHeartwings(websiteSettingDO);
        websiteSettingDAO.upsert(websiteSettingDO);
        return ResultUtil.success();
    }

    /***
     * 添加心语记录
     * @param websiteSettingDO WebsiteSettingDO
     */
    private void addHeartwings(WebsiteSettingDO websiteSettingDO) {
        WebsiteSettingDO websiteSettingDO1 = websiteSettingDAO.findOne();
        if (websiteSettingDO1 != null) {
            String oldHeartwings = websiteSettingDO1.getBackgroundTextSite();
            String heartwings = websiteSettingDO.getBackgroundTextSite();
            if (!CharSequenceUtil.isBlank(oldHeartwings) && !oldHeartwings.equals(heartwings)) {
                HeartwingsDO heartwingsDO = new HeartwingsDO();
                heartwingsDO.setCreateTime(LocalDateTimeUtil.now());
                heartwingsDO.setCreator(userLoginHolder.getUserId());
                heartwingsDO.setUsername(userLoginHolder.getUsername());
                heartwingsDO.setHeartwings(heartwings);
                heartwingsDAO.save(heartwingsDO);
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
        WebsiteSettingDO websiteSettingDO = websiteSettingDAO.findOne();
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
        return heartwingsDAO.getWebsiteHeartwings(page, pageSize, order);
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
        accessTokenDAO.generateAccessToken(userAccessTokenDO);
        return ResultUtil.success(accessToken);
    }

    /***
     * accessToken列表
     * @param username 用户名
     * @return List<UserAccessTokenDTO>
     */
    public ResponseResult<List<UserAccessTokenDTO>> accessTokenList(String username) {
        List<UserAccessTokenDTO> list = accessTokenDAO.accessTokenList(username);
        return ResultUtil.success(list);
    }

    /***
     * 删除accessToken
     * @param id accessTokenId
     */
    public void deleteAccessToken(String id) {
        accessTokenDAO.deleteAccessToken(id);
    }

    public void resetMenuAndRole() {
        menuService.initMenus();
        roleService.initRoles();
    }

    public WebsiteSettingDTO getPreviewConfig() {
        WebsiteSettingDO websiteSettingDO = websiteSettingDAO.getPreviewConfig();
        return websiteSettingDO.toWebsiteSettingDTO();
    }

    public synchronized void updatePreviewConfig(WebsiteSettingDTO websiteSettingDTO) {
        websiteSettingDAO.updatePreviewConfig(websiteSettingDTO.getIframe());
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
            Completable.fromAction(() -> {
                try {
                    // 清除之前的文件夹大小数据
                    folderSizeDAO.clearFolderSizInDb();
                    long totalSize = folderSizeDAO.totalSizeNeedUpdateSizeInDb();
                    calculateFolderSizeProcessedCount.set(0);
                    HybridThrottleExecutor hybridThrottleExecutor = new HybridThrottleExecutor(THROTTLE_DELAY_MS);
                    // 调用实际的循环处理逻辑
                    processCalculateFolderSizeLoop(totalSize, hybridThrottleExecutor, notifyUsername);
                    hybridThrottleExecutor.shutdown();
                } finally {
                    calculateFolderSizeScheduled.set(false);
                    // 关键：检查在本次处理运行期间是否有新的文件夹被标记
                    // 如果有，则再次尝试调度，确保它们得到处理
                    if (folderSizeDAO.hasNeedUpdateSizeInDb()) {
                        ensureProcessCalculateFolderSize();
                    }
                }

            }).subscribeOn(Schedulers.io())
                    .subscribe();
        }
    }

    private void processCalculateFolderSizeLoop(long totalSize, HybridThrottleExecutor hybridThrottleExecutor, String notifyUsername) {
        boolean run = true;
        while (run && !Thread.currentThread().isInterrupted()) {
            // 查询所有需要更新大小的文件夹
            List<FileDocument> tasks = folderSizeDAO.findFoldersNeedUpdateSize(FOLDER_BATCH_SIZE);
            if (tasks.isEmpty()) {
                run = false;
                messageService.pushMessage(notifyUsername, 100, "calculateFolderSizeProcessed");
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
                    long size = etagService.getFolderSize(folderDoc.getUserId(), currentFolderNormalizedPath);
                    // 更新数据库中的大小
                    folderSizeDAO.updateFileSize(folderDoc.getId(), size);

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

    public boolean getMfaForceEnable() {
        WebsiteSettingDO websiteSettingDO = websiteSettingDAO.findOne();
        if (websiteSettingDO != null) {
            return BooleanUtil.isTrue(websiteSettingDO.getMfaForceEnable());
        }
        return false;
    }

    public void setMfaForceEnable(Boolean mfaForceEnable) {
        websiteSettingDAO.setMfaForceEnable(mfaForceEnable);
    }
}

package com.jmal.clouddisk.dao.impl.jpa;

import com.jmal.clouddisk.dao.IWebsiteSettingDAO;
import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import com.jmal.clouddisk.dao.impl.jpa.repository.WebsiteSettingRepository;
import com.jmal.clouddisk.model.WebsiteSettingDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Repository
@RequiredArgsConstructor
@Conditional(RelationalDataSourceCondition.class)
public class WebsiteSettingDAOJpaImpl implements IWebsiteSettingDAO {

    private final WebsiteSettingRepository websiteSettingRepository;

    @Override
    public WebsiteSettingDO findOne() {
        log.debug("查询网站设置");

        try {
            Optional<WebsiteSettingDO> setting = websiteSettingRepository.findOne();

            if (setting.isPresent()) {
                log.debug("找到网站设置: id={}, siteName={}",
                         setting.get().getId(), setting.get().getSiteName());
                return setting.get();
            } else {
                return websiteSettingRepository.save(new WebsiteSettingDO());
            }
        } catch (Exception e) {
            log.error("查询网站设置失败: {}", e.getMessage(), e);
            throw new RuntimeException("查询网站设置失败", e);
        }
    }

    @Override
    @Transactional
    public void updateLogo(String logo) {
        log.debug("更新网站Logo: logo={}", logo);

        try {
            validateLogoParameter(logo);

            int updatedCount = websiteSettingRepository.updateNetdiskLogo(logo);

            if (updatedCount == 0) {
                createDefaultSettingWithLogo(logo);
            } else {
                log.debug("Logo更新成功，影响行数: {}", updatedCount);
            }
        } catch (Exception e) {
            log.error("更新网站Logo失败: logo={}, error={}", logo, e.getMessage(), e);
            throw new RuntimeException("更新Logo失败", e);
        }
    }

    @Override
    @Transactional
    public void updateName(String name) {
        log.debug("更新网站名称: name={}", name);

        try {
            validateNameParameter(name);

            int updatedCount = websiteSettingRepository.updateNetdiskName(name);

            if (updatedCount == 0) {
                createDefaultSettingWithName(name);
            } else {
                log.debug("网站名称更新成功，影响行数: {}", updatedCount);
            }
        } catch (Exception e) {
            log.error("更新网站名称失败: name={}, error={}", name, e.getMessage(), e);
            throw new RuntimeException("更新网站名称失败", e);
        }
    }

    @Override
    @Transactional
    public void upsert(WebsiteSettingDO websiteSettingDO) {
        log.debug("更新或插入网站设置: siteName={}", websiteSettingDO.getSiteName());

        try {
            validateWebsiteSettingDO(websiteSettingDO);

            // 检查是否存在现有记录
            Optional<WebsiteSettingDO> existingSetting = websiteSettingRepository.findOne();

            if (existingSetting.isPresent()) {
                // 更新现有记录
                WebsiteSettingDO existing = existingSetting.get();
                updateExistingSettings(existing, websiteSettingDO);

                WebsiteSettingDO savedSetting = websiteSettingRepository.save(existing);
                log.debug("网站设置更新成功: id={}, siteName={}", savedSetting.getId(), savedSetting.getSiteName());
            } else {

                if (websiteSettingDO.getId() == null) {
                    websiteSettingDO.setId(generateId());
                }

                WebsiteSettingDO savedSetting = websiteSettingRepository.save(websiteSettingDO);
                log.debug("网站设置创建成功: id={}, siteName={}",
                         savedSetting.getId(), savedSetting.getSiteName());
            }
        } catch (Exception e) {
            log.error("更新或插入网站设置失败: siteName={}, error={}",
                     websiteSettingDO.getSiteName(), e.getMessage(), e);
            throw new RuntimeException("更新网站设置失败", e);
        }
    }

    @Override
    public WebsiteSettingDO getPreviewConfig() {
        log.debug("获取预览配置");

        try {
            Optional<WebsiteSettingDO> setting = websiteSettingRepository.findOne();

            if (setting.isPresent()) {
                WebsiteSettingDO previewConfig = setting.get();
                log.debug("获取预览配置成功: id={}, iframe存在={}",
                         previewConfig.getId(),
                         previewConfig.getIframe() != null && !previewConfig.getIframe().isEmpty());
                return previewConfig;
            } else {
                log.debug("未找到预览配置");
                return null;
            }
        } catch (Exception e) {
            log.error("获取预览配置失败: {}", e.getMessage(), e);
            throw new RuntimeException("获取预览配置失败", e);
        }
    }

    @Override
    @Transactional
    public void updatePreviewConfig(String iframe) {
        log.debug("更新预览配置: iframe长度={}", iframe != null ? iframe.length() : 0);

        try {
            if (iframe == null || iframe.isBlank()) {
                log.debug("iframe为空或空白，跳过更新");
                return;
            }

            validateIframeParameter(iframe);

            int updatedCount = websiteSettingRepository.updateIframe(iframe.trim());

            if (updatedCount == 0) {
                createDefaultSettingWithIframe(iframe.trim());
            } else {
                log.debug("预览配置更新成功，影响行数: {}", updatedCount);
            }
        } catch (Exception e) {
            log.error("更新预览配置失败: iframe长度={}, error={}",
                     iframe != null ? iframe.length() : 0, e.getMessage(), e);
            throw new RuntimeException("更新预览配置失败", e);
        }
    }

    @Override
    @Transactional
    public void setMfaForceEnable(Boolean mfaForceEnable) {
        log.debug("设置MFA强制启用: mfaForceEnable={}", mfaForceEnable);

        try {
            if (mfaForceEnable == null) {
                log.warn("mfaForceEnable参数为null，使用默认值false");
                mfaForceEnable = false;
            }

            int updatedCount = websiteSettingRepository.updateMfaForceEnable(mfaForceEnable);

            if (updatedCount == 0) {
                // 如果没有更新任何记录，创建新的设置记录
                log.debug("未找到现有设置记录，创建新记录");
                createDefaultSettingWithMfa(mfaForceEnable);
            } else {
                log.debug("MFA强制启用设置更新成功，影响行数: {}", updatedCount);
            }
        } catch (Exception e) {
            log.error("设置MFA强制启用失败: mfaForceEnable={}, error={}",
                     mfaForceEnable, e.getMessage(), e);
            throw new RuntimeException("设置MFA强制启用失败", e);
        }
    }

    /**
     * 更新现有设置的所有字段
     */
    private void updateExistingSettings(WebsiteSettingDO existing, WebsiteSettingDO newSettings) {
        existing.setBackgroundSite(newSettings.getBackgroundSite());
        existing.setBackgroundTextSite(newSettings.getBackgroundTextSite());
        existing.setBackgroundDescSite(newSettings.getBackgroundDescSite());
        existing.setNetdiskLogo(newSettings.getNetdiskLogo());
        existing.setNetdiskName(newSettings.getNetdiskName());
        existing.setSiteUrl(newSettings.getSiteUrl());
        existing.setSiteIco(newSettings.getSiteIco());
        existing.setSiteLogo(newSettings.getSiteLogo());
        existing.setSiteName(newSettings.getSiteName());
        existing.setAlonePages(newSettings.getAlonePages());
        existing.setOperatingButtons(newSettings.getOperatingButtons());
        existing.setCategoryBackground(newSettings.getCategoryBackground());
        existing.setArchiveBackground(newSettings.getArchiveBackground());
        existing.setTagBackground(newSettings.getTagBackground());
        existing.setCopyright(newSettings.getCopyright());
        existing.setRecordPermissionNum(newSettings.getRecordPermissionNum());
        existing.setNetworkRecordNumber(newSettings.getNetworkRecordNumber());
        existing.setNetworkRecordNumberStr(newSettings.getNetworkRecordNumberStr());
        existing.setFooterHtml(newSettings.getFooterHtml());
        existing.setIframe(newSettings.getIframe());
        existing.setMfaForceEnable(newSettings.getMfaForceEnable());
    }

    /**
     * 创建带Logo的默认设置
     */
    private void createDefaultSettingWithLogo(String logo) {
        WebsiteSettingDO setting = createDefaultWebsiteSetting();
        setting.setNetdiskLogo(logo);
        websiteSettingRepository.save(setting);
        log.debug("创建带Logo的默认设置成功: logo={}", logo);
    }

    /**
     * 创建带名称的默认设置
     */
    private void createDefaultSettingWithName(String name) {
        WebsiteSettingDO setting = createDefaultWebsiteSetting();
        setting.setNetdiskName(name);
        websiteSettingRepository.save(setting);
        log.debug("创建带名称的默认设置成功: name={}", name);
    }

    /**
     * 创建带iframe的默认设置
     */
    private void createDefaultSettingWithIframe(String iframe) {
        WebsiteSettingDO setting = createDefaultWebsiteSetting();
        setting.setIframe(iframe);
        websiteSettingRepository.save(setting);
        log.debug("创建带iframe的默认设置成功");
    }

    /**
     * 创建带MFA设置的默认设置
     */
    private void createDefaultSettingWithMfa(Boolean mfaForceEnable) {
        WebsiteSettingDO setting = createDefaultWebsiteSetting();
        setting.setMfaForceEnable(mfaForceEnable);
        websiteSettingRepository.save(setting);
        log.debug("创建带MFA设置的默认设置成功: mfaForceEnable={}", mfaForceEnable);
    }

    /**
     * 创建默认的网站设置
     */
    private WebsiteSettingDO createDefaultWebsiteSetting() {
        return new WebsiteSettingDO();
    }

    /**
     * 生成ID
     */
    private String generateId() {
        return java.util.UUID.randomUUID().toString().replace("-", "");
    }

    // 验证方法
    private void validateLogoParameter(String logo) {
        if (logo == null || logo.trim().isEmpty()) {
            throw new IllegalArgumentException("Logo不能为空");
        }
        if (logo.length() > 500) {
            throw new IllegalArgumentException("Logo路径长度不能超过500个字符");
        }
    }

    private void validateNameParameter(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("网站名称不能为空");
        }
        if (name.length() > 100) {
            throw new IllegalArgumentException("网站名称长度不能超过100个字符");
        }
    }

    private void validateIframeParameter(String iframe) {
        if (iframe.length() > 10000) {
            throw new IllegalArgumentException("iframe内容长度不能超过10000个字符");
        }
    }

    private void validateWebsiteSettingDO(WebsiteSettingDO websiteSettingDO) {
        if (websiteSettingDO == null) {
            throw new IllegalArgumentException("网站设置对象不能为空");
        }
    }
}

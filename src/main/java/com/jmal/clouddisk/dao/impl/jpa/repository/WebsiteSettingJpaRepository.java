package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.dao.config.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.WebsiteSettingDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@JpaRepositoryMarker
@Conditional(RelationalDataSourceCondition.class)
public interface WebsiteSettingJpaRepository extends JpaRepository<WebsiteSettingDO, String> {

    /**
     * 查找最新的网站设置（按创建时间排序）
     */
    Optional<WebsiteSettingDO> findFirstByOrderByCreatedTimeDesc();

    /**
     * 查找最新的网站设置（按更新时间排序）
     */
    Optional<WebsiteSettingDO> findFirstByOrderByUpdatedTimeDesc();

    /**
     * 更新网盘Logo
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET w.netdiskLogo = :logo, w.updatedTime = CURRENT_TIMESTAMP")
    int updateNetdiskLogo(@Param("logo") String logo);

    /**
     * 更新网盘名称
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET w.netdiskName = :name, w.updatedTime = CURRENT_TIMESTAMP")
    int updateNetdiskName(@Param("name") String name);

    /**
     * 更新iframe配置
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET w.iframe = :iframe, w.updatedTime = CURRENT_TIMESTAMP")
    int updateIframe(@Param("iframe") String iframe);

    /**
     * 更新MFA强制启用设置
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET w.mfaForceEnable = :mfaForceEnable, w.updatedTime = CURRENT_TIMESTAMP")
    int updateMfaForceEnable(@Param("mfaForceEnable") Boolean mfaForceEnable);

    /**
     * 更新站点名称
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET w.siteName = :siteName, w.updatedTime = CURRENT_TIMESTAMP")
    int updateSiteName(@Param("siteName") String siteName);

    /**
     * 更新站点URL
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET w.siteUrl = :siteUrl, w.updatedTime = CURRENT_TIMESTAMP")
    int updateSiteUrl(@Param("siteUrl") String siteUrl);

    /**
     * 更新站点Logo
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET w.siteLogo = :siteLogo, w.updatedTime = CURRENT_TIMESTAMP")
    int updateSiteLogo(@Param("siteLogo") String siteLogo);

    /**
     * 更新站点图标
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET w.siteIco = :siteIco, w.updatedTime = CURRENT_TIMESTAMP")
    int updateSiteIco(@Param("siteIco") String siteIco);

    /**
     * 更新版权信息
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET w.copyright = :copyright, w.updatedTime = CURRENT_TIMESTAMP")
    int updateCopyright(@Param("copyright") String copyright);

    /**
     * 更新备案许可号
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET w.recordPermissionNum = :recordPermissionNum, w.updatedTime = CURRENT_TIMESTAMP")
    int updateRecordPermissionNum(@Param("recordPermissionNum") String recordPermissionNum);

    /**
     * 更新公网备案号
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET w.networkRecordNumber = :networkRecordNumber, w.networkRecordNumberStr = :networkRecordNumberStr, w.updatedTime = CURRENT_TIMESTAMP")
    int updateNetworkRecordNumber(@Param("networkRecordNumber") String networkRecordNumber,
                                 @Param("networkRecordNumberStr") String networkRecordNumberStr);

    /**
     * 批量更新背景设置
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET " +
           "w.backgroundSite = :backgroundSite, " +
           "w.categoryBackground = :categoryBackground, " +
           "w.archiveBackground = :archiveBackground, " +
           "w.tagBackground = :tagBackground, " +
           "w.updatedTime = CURRENT_TIMESTAMP")
    int updateBackgroundSettings(@Param("backgroundSite") String backgroundSite,
                                @Param("categoryBackground") String categoryBackground,
                                @Param("archiveBackground") String archiveBackground,
                                @Param("tagBackground") String tagBackground);

    /**
     * 检查是否存在任何网站设置
     */
    @Query("SELECT COUNT(w) > 0 FROM WebsiteSettingDO w")
    boolean existsAnySettings();

    /**
     * 获取网站基本信息（只包含必要字段）
     */
    @Query("SELECT w.siteName, w.siteUrl, w.siteLogo, w.siteIco FROM WebsiteSettingDO w ORDER BY w.updatedTime DESC LIMIT 1")
    Object[] findBasicSiteInfo();

    /**
     * 获取MFA设置
     */
    @Query("SELECT w.mfaForceEnable FROM WebsiteSettingDO w ORDER BY w.updatedTime DESC LIMIT 1")
    Optional<Boolean> findMfaForceEnable();
}

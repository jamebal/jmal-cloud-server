package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * @author jmal
 * @Description 网站设置
 * @Date 2020/11/5 2:45 下午
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Document(collection = "websiteSetting")
@Entity
@Table(name = "website_setting")
public class WebsiteSettingDO extends AuditableEntity implements Reflective {

    /***
     * 站点背景大图
     */
    String backgroundSite;
    /***
     * 首页大图内文字 / 心语
     */
    String backgroundTextSite;
    /***
     * 首页大图内描述
     */
    String backgroundDescSite;
    /***
     * 网盘logo文件名
     */
    String netdiskLogo;
    /***
     * 网盘名称
     */
    String netdiskName;
    /***
     * 站点地址
     */
    String siteUrl;
    /***
     * 网站 图标
     */
    String siteIco;
    /***
     * 网站 Logo
     */
    String siteLogo;
    /***
     * 站点名称
     */
    String siteName;
    /***
     * 需要显示的独立页面
     */
    List<String> alonePages;
    /***
     * 操作按钮
     */
    String operatingButtons;
    /***
     * 分类页面背景
     */
    String categoryBackground;
    /***
     * 归档页面背景
     */
    String archiveBackground;
    /***
     * 标签页面背景
     */
    String tagBackground;
    /***
     * 版权信息
     */
    String copyright;
    /***
     * 备案许可号
     */
    String recordPermissionNum;
    /**
     * 公网备案号
     */
    String networkRecordNumber;

    /**
     * 公网备案号 展示文本
     */
    String networkRecordNumberStr;

    /**
     * 页脚html
     */
    String footerHtml;

    /**
     * iframe预览配置
     */
    String iframe;

    @Schema(name = "forceEnable", title = "是否强制启用多因素认证")
    Boolean mfaForceEnable;

    public WebsiteSettingDTO toWebsiteSettingDTO() {
        WebsiteSettingDTO websiteSettingDTO = new WebsiteSettingDTO();
        websiteSettingDTO.setBackgroundSite(this.backgroundSite);
        websiteSettingDTO.setBackgroundTextSite(this.backgroundTextSite);
        websiteSettingDTO.setBackgroundDescSite(this.backgroundDescSite);
        websiteSettingDTO.setNetdiskLogo(this.netdiskLogo);
        websiteSettingDTO.setNetdiskName(this.netdiskName);
        websiteSettingDTO.setSiteUrl(this.siteUrl);
        websiteSettingDTO.setSiteIco(this.siteIco);
        websiteSettingDTO.setSiteLogo(this.siteLogo);
        websiteSettingDTO.setSiteName(this.siteName);
        websiteSettingDTO.setAlonePages(this.alonePages);
        websiteSettingDTO.setOperatingButtons(this.operatingButtons);
        websiteSettingDTO.setCategoryBackground(this.categoryBackground);
        websiteSettingDTO.setArchiveBackground(this.archiveBackground);
        websiteSettingDTO.setTagBackground(this.tagBackground);
        websiteSettingDTO.setCopyright(this.copyright);
        websiteSettingDTO.setRecordPermissionNum(this.recordPermissionNum);
        websiteSettingDTO.setNetworkRecordNumber(this.networkRecordNumber);
        websiteSettingDTO.setNetworkRecordNumberStr(this.networkRecordNumberStr);
        websiteSettingDTO.setFooterHtml(this.footerHtml);
        websiteSettingDTO.setIframe(this.iframe);
        websiteSettingDTO.setMfaForceEnable(this.mfaForceEnable);
        return websiteSettingDTO;
    }
}

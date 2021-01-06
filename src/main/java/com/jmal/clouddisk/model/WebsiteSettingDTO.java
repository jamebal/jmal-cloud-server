package com.jmal.clouddisk.model;

import lombok.Data;

import java.util.List;

/**
 * @author jmal
 * @Description 网站设置DTO
 * @Date 2020/11/5 3:58 下午
 */
@Data
public class WebsiteSettingDTO {
    String userId;
    /***
     * 站点背景大图
     */
    String backgroundSite;
    /***
     * 首页大图内文字
     */
    String backgroundTextSite;
    /***
     * 首页大图内描述
     */
    String backgroundDescSite;
    /***
     * 网站 Logo / 站点名称
     */
    String siteName;
    /***
     * 需要显示的独立页面
     */
    List<String> alonePages;
    /***
     * 操作按钮(原始字符串)
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
     * 操作按钮对象集合
     */
    List<OperatingButton> operatingButtonList;

    /***
     * 版权信息
     */
    String copyright;
    /***
     * 备案许可号
     */
    String recordPermissionNum;

    @Data
    public static class OperatingButton {
        /***
         * 按钮名称
         */
        String title;
        /***
         * 按钮图标class
         */
        String style;
        /***
         * 点击按钮后跳转的地址
         */
        String url;
    }

    public boolean isShowAlonePage(String page){
        return alonePages.contains(page);
    }
}

package com.jmal.clouddisk.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @author jmal
 * @Description 用户设置参数
 * @Date 2020/11/5 2:45 下午
 */
@Data
public class WebsiteSetting {

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
}

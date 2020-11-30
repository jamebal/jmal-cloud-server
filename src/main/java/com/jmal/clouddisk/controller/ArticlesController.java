package com.jmal.clouddisk.controller;

import cn.hutool.core.lang.Console;
import cn.hutool.core.util.ReUtil;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.WebsiteSettingDTO;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.impl.CategoryService;
import com.jmal.clouddisk.service.impl.SettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;

/**
 * @author jmal
 * @Description 文章页面
 * @Date 2020/11/16 5:41 下午
 */
@CrossOrigin(origins = "*", maxAge = 3600)
@Controller
public class ArticlesController {

    @Autowired
    private SettingService settingService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private IFileService fileService;

    @GetMapping("/articles")
    public String articles(HttpServletRequest request, ModelMap map){
        int page = 1, pageSize = 10;
        String pIndex = request.getParameter("page");
        if(!StringUtils.isEmpty(pIndex)){
            page = Integer.parseInt(pIndex);
        }
        getSetting(map);
        map.addAttribute("articlesData", fileService.getArticles(page, pageSize));
        map.addAttribute("darkTheme", darkTheme(request));
        return "index";
    }

    private boolean darkTheme(HttpServletRequest request) {
        Cookie[] cookies =  request.getCookies();
        if(cookies != null){
            for(Cookie cookie : cookies){
                if("jmal-theme".equals(cookie.getName())){
                    if("dark".equals(cookie.getValue())){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @GetMapping("/articles/{slug}")
    public String index(HttpServletRequest request, @PathVariable String slug, ModelMap map){
        getSetting(map);
        FileDocument fileDocument = fileService.getMarkDownContentBySlug(slug);
        if(fileDocument == null){
            return "error";
        }
        map.addAttribute("markdown", fileDocument);
        map.addAttribute("darkTheme", darkTheme(request));
        return "article";
    }

    @GetMapping("/articles/categories")
    public String categories(HttpServletRequest request, ModelMap map){
        getSetting(map);
        map.addAttribute("categories", new FileDocument());
        map.addAttribute("darkTheme", darkTheme(request));
        return "categories";
    }

    private void getSetting(ModelMap map) {
        WebsiteSettingDTO websiteSettingDTO = settingService.getWebsiteSetting();
        setOperatingButtonList(websiteSettingDTO);
        map.addAttribute("setting", websiteSettingDTO);
    }

    /***
     * 解析设置里的文本为操作按钮列表
     * @param websiteSettingDTO userSettingDTO
     */
    private void setOperatingButtonList(WebsiteSettingDTO websiteSettingDTO) {
        if(websiteSettingDTO != null && !StringUtils.isEmpty(websiteSettingDTO.getOperatingButtons())){
            String operatingButtons = websiteSettingDTO.getOperatingButtons();
            List<WebsiteSettingDTO.OperatingButton> operatingButtonList = new ArrayList<>();
            for (String button : operatingButtons.split("[\\n]")) {
                WebsiteSettingDTO.OperatingButton operatingButton = new WebsiteSettingDTO.OperatingButton();
                int splitIndex = button.indexOf(":");
                String label = button.substring(0, splitIndex);
                String title = ReUtil.getGroup0("[^><]+(?=<\\/i>)", label);
                if(StringUtils.isEmpty(title)){
                    title = "";
                }
                operatingButton.setTitle(title);
                operatingButton.setStyle(ReUtil.getGroup0("[^=\"<]+(?=\">)", label));
                operatingButton.setUrl(button.substring(splitIndex + 1));
                operatingButtonList.add(operatingButton);
            }
            websiteSettingDTO.setOperatingButtonList(operatingButtonList);
        }
    }

}



package com.jmal.clouddisk.controller;

import cn.hutool.core.util.ReUtil;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.IMarkdownService;
import com.jmal.clouddisk.service.impl.CategoryService;
import com.jmal.clouddisk.service.impl.SettingService;
import com.jmal.clouddisk.service.impl.TagService;
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
    private TagService tagService;

    @Autowired
    private IMarkdownService fileService;

    @GetMapping("/articles")
    public String articles(HttpServletRequest request, ModelMap map){
        getSetting(map);
        int page = 1, pageSize = 10;
        String pIndex = request.getParameter("page");
        if(!StringUtils.isEmpty(pIndex)){
            page = Integer.parseInt(pIndex);
        }
        map.addAttribute("articlesData", fileService.getArticles(page, pageSize));
        map.addAttribute("darkTheme", darkTheme(request));
        return "index";
    }

    @GetMapping("/articles/o/{slug}")
    public String alonePage(HttpServletRequest request, @PathVariable String slug, ModelMap map){
        return articlePage(request, slug, map);
    }

    @GetMapping("/articles/s/{slug}")
    public String article(HttpServletRequest request, @PathVariable String slug, ModelMap map){
        return articlePage(request, slug, map);
    }

    private String articlePage(HttpServletRequest request, String slug, ModelMap map) {
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
        map.addAttribute("categories", categoryService.list(null, null));
        map.addAttribute("darkTheme", darkTheme(request));
        return "categories";
    }

    @GetMapping("/articles/archives")
    public String archives(HttpServletRequest request, ModelMap map){
        getSetting(map);
        int page = 1, pageSize = 100;
        String pIndex = request.getParameter("page");
        if(!StringUtils.isEmpty(pIndex)){
            page = Integer.parseInt(pIndex);
        }
        map.addAttribute("articlesData", fileService.getArchives(page, pageSize));
        map.addAttribute("darkTheme", darkTheme(request));
        return "archives";
    }

    @GetMapping("/articles/categories/{categoryName}")
    public String getCategoryByName(HttpServletRequest request, ModelMap map, @PathVariable String categoryName){
        getSetting(map);
        if (StringUtils.isEmpty(categoryName)){
            return "error";
        }
        String categoryId = null;
        if (!StringUtils.isEmpty(categoryName)) {
            Category category = categoryService.getCategoryInfo(null, categoryName);
            if (category == null) {
                return "error";
            }
            map.addAttribute("category", category);
            categoryId = category.getId();
        }
        int page = 1, pageSize = 10;
        String pIndex = request.getParameter("page");
        if(!StringUtils.isEmpty(pIndex)){
            page = Integer.parseInt(pIndex);
        }
        map.addAttribute("articlesData", fileService.getArticlesByCategoryId(page, pageSize, categoryId));
        map.addAttribute("darkTheme", darkTheme(request));
        return "category";
    }

    @GetMapping("/articles/tags")
    public String tags(HttpServletRequest request, ModelMap map){
        getSetting(map);
        map.addAttribute("tags", tagService.listTagsOfArticle());
        map.addAttribute("darkTheme", darkTheme(request));
        return "tags";
    }

    @GetMapping("/articles/tags/{tagName}")
    public String getTagByName(HttpServletRequest request, ModelMap map, @PathVariable String tagName){
        getSetting(map);
        if (StringUtils.isEmpty(tagName)){
            return "error";
        }
        String tagId = null;
        if (!StringUtils.isEmpty(tagName)) {
            Tag tag = tagService.getTagInfo(null, tagName);
            if (tag == null) {
                return "error";
            }
            map.addAttribute("tag", tag);
            tagId = tag.getId();
        }
        int page = 1, pageSize = 10;
        String pIndex = request.getParameter("page");
        if(!StringUtils.isEmpty(pIndex)){
            page = Integer.parseInt(pIndex);
        }
        map.addAttribute("articlesData", fileService.getArticlesByTagId(page, pageSize, tagId));
        map.addAttribute("darkTheme", darkTheme(request));
        return "tag";
    }

    private void getSetting(ModelMap map) {
        WebsiteSettingDTO websiteSettingDTO = settingService.getWebsiteSetting();
        setOperatingButtonList(websiteSettingDTO);
        List<MarkdownVO> markdownVOList = fileService.getAlonePages();
        map.addAttribute("alonePages", markdownVOList);
        map.addAttribute("setting", websiteSettingDTO);
        int alonePageShowIndex = 4 - websiteSettingDTO.getAlonePages().size();
        map.addAttribute("showAlonePages", markdownVOList.subList(0, alonePageShowIndex));
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

}



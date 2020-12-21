package com.jmal.clouddisk.controller;

import cn.hutool.core.util.ReUtil;
import com.jmal.clouddisk.model.*;
import com.jmal.clouddisk.service.IMarkdownService;
import com.jmal.clouddisk.service.IUserService;
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
import org.springframework.web.bind.annotation.RequestParam;

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

    @Autowired
    IUserService userService;

    @GetMapping("/public/404")
    public String notFind(HttpServletRequest request, ModelMap map){
        getSetting(request, map);
        return "404";
    }

    @GetMapping("/articles")
    public String articles(HttpServletRequest request, ModelMap map){
        getSetting(request, map);
        int page = 1, pageSize = 10;
        String pIndex = request.getParameter("page");
        if(!StringUtils.isEmpty(pIndex)){
            page = Integer.parseInt(pIndex);
        }
        map.addAttribute("articlesData", fileService.getArticles(page, pageSize));
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
        getSetting(request, map);
        ArticleVO articleVO = fileService.getMarkDownContentBySlug(slug);
        if(articleVO == null){
            return "404";
        }
        Cookie[] cookies =  request.getCookies();
        if(cookies != null){
            for(Cookie cookie : cookies){
                if("consumerId".equals(cookie.getName())){
                    articleVO.setEditable(true);
                }
            }
        }
        map.addAttribute("markdown", articleVO);
        return "article";
    }

    @GetMapping("/articles/categories")
    public String categories(HttpServletRequest request, ModelMap map){
        getSetting(request, map);
        map.addAttribute("categories", categoryService.list(null, null));
        return "categories";
    }

    @GetMapping("/articles/archives")
    public String archives(HttpServletRequest request, ModelMap map){
        getSetting(request, map);
        int page = 1, pageSize = 100;
        String pIndex = request.getParameter("page");
        if(!StringUtils.isEmpty(pIndex)){
            page = Integer.parseInt(pIndex);
        }
        map.addAttribute("articlesData", fileService.getArchives(page, pageSize));
        return "archives";
    }

    @GetMapping("/articles/categories/{categorySlugName}")
    public String getCategoryByName(HttpServletRequest request, ModelMap map, @PathVariable String categorySlugName){
        getSetting(request, map);
        if (StringUtils.isEmpty(categorySlugName)){
            return "404";
        }
        String categoryId = null;
        if (!StringUtils.isEmpty(categorySlugName)) {
            Category category = categoryService.getCategoryInfoBySlug(null, categorySlugName);
            if (category == null) {
                return "404";
            }
            map.addAttribute("query", category.toArticlesQuery());
            categoryId = category.getId();
        }
        int page = 1, pageSize = 10;
        String pIndex = request.getParameter("page");
        if(!StringUtils.isEmpty(pIndex)){
            page = Integer.parseInt(pIndex);
        }
        map.addAttribute("articlesData", fileService.getArticlesByCategoryId(page, pageSize, categoryId));
        return "articles-query";
    }

    @GetMapping("/articles/tags")
    public String tags(HttpServletRequest request, ModelMap map){
        getSetting(request, map);
        map.addAttribute("tags", tagService.listTagsOfArticle());
        return "tags";
    }

    @GetMapping("/articles/tags/{tagSlugName}")
    public String getTagByName(HttpServletRequest request, ModelMap map, @PathVariable String tagSlugName){
        getSetting(request, map);
        if (StringUtils.isEmpty(tagSlugName)){
            return "404";
        }
        String tagId = null;
        if (!StringUtils.isEmpty(tagSlugName)) {
            Tag tag = tagService.getTagInfoBySlug(null, tagSlugName);
            if (tag == null) {
                return "404";
            }
            map.addAttribute("query", tag.toArticlesQuery());
            tagId = tag.getId();
        }
        int page = 1, pageSize = 10;
        String pIndex = request.getParameter("page");
        if(!StringUtils.isEmpty(pIndex)){
            page = Integer.parseInt(pIndex);
        }
        map.addAttribute("articlesData", fileService.getArticlesByTagId(page, pageSize, tagId));
        return "articles-query";
    }

    @GetMapping("/articles/search")
    public String search(HttpServletRequest request, ModelMap map, @RequestParam String keyword){
        getSetting(request, map);
        int page = 1, pageSize = 10;
        String pIndex = request.getParameter("page");
        if(!StringUtils.isEmpty(pIndex)){
            page = Integer.parseInt(pIndex);
        }
        ArticlesQuery query = new ArticlesQuery();
        Page<List<MarkdownVO>> articles = fileService.getArticlesByKeyword(page, pageSize, keyword);
        if(!articles.isEmpty()){
            MarkdownVO markdownVO = articles.getData().get(0);
            query.setBackground(markdownVO.getCover());
        }
        query.setName("包含关键字 "+keyword+" 的文章");
        map.addAttribute("query", query);
        map.addAttribute("articlesData", articles);
        return "articles-query";
    }

    @GetMapping("/articles/author/{username}")
    public String author(HttpServletRequest request, ModelMap map, @PathVariable String username){
        getSetting(request, map);
        String userId = userService.getUserIdByUserName(username);
        if(StringUtils.isEmpty(userId)){
            return "404";
        }
        int page = 1, pageSize = 10;
        String pIndex = request.getParameter("page");
        if(!StringUtils.isEmpty(pIndex)){
            page = Integer.parseInt(pIndex);
        }
        ArticlesQuery query = new ArticlesQuery();
        Page<List<MarkdownVO>> articles = fileService.getArticlesByAuthor(page, pageSize, userId);
        if(!articles.isEmpty()){
            MarkdownVO markdownVO = articles.getData().get(0);
            query.setBackground(markdownVO.getCover());
        }
        query.setName(username + " 发布的文章");
        map.addAttribute("query", query);
        map.addAttribute("articlesData", articles);
        return "articles-query";
    }

    private void getSetting(HttpServletRequest request, ModelMap map) {
        WebsiteSettingDTO websiteSettingDTO = settingService.getWebsiteSetting();
        setOperatingButtonList(websiteSettingDTO);
        List<MarkdownVO> markdownVOList = fileService.getAlonePages();
        map.addAttribute("alonePages", markdownVOList);
        map.addAttribute("setting", websiteSettingDTO);
        int alonePageShowIndex = 4 - websiteSettingDTO.getAlonePages().size();
        if(!markdownVOList.isEmpty()){
            markdownVOList = markdownVOList.subList(0, alonePageShowIndex);
        }
        map.addAttribute("showAlonePages", markdownVOList);
        map.addAttribute("darkTheme", darkTheme(request));
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



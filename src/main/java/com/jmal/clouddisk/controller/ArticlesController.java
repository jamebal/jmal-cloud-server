package com.jmal.clouddisk.controller;

import cn.hutool.core.util.ReUtil;
import com.jmal.clouddisk.annotation.LogOperatingFun;
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

    private static final String X_PJAX = "X-PJAX";
    private static final String X_PJAX_TRUE = "true";

    @GetMapping("/public/404")
    @LogOperatingFun(value = "404", logType = LogOperation.Type.ARTICLE)
    public String notFind(HttpServletRequest request, ModelMap map) {
        boolean isPjax = pjaxMap(request, map, "404");
        map.addAttribute("titleName", "页面没有找到");
        return isPjax ? "404" : "index";
    }

    @GetMapping("/articles")
    @LogOperatingFun(value = "文章列表", logType = LogOperation.Type.ARTICLE)
    public String index(HttpServletRequest request, ModelMap map) {
        map.addAttribute("mark", "articles");
        boolean isPjax = isPjax(request);
        WebsiteSettingDTO websiteSettingDTO;
        if(!isPjax){
            websiteSettingDTO = getSetting(request, map);
        } else {
            websiteSettingDTO = settingService.getWebsiteSetting();
            map.addAttribute("setting", settingService.getWebsiteSetting());
        }
        map.addAttribute("titleName", websiteSettingDTO.getSiteName());
        int page = 1, pageSize = 10;
        String pIndex = request.getParameter("page");
        if (!StringUtils.isEmpty(pIndex)) {
            page = Integer.parseInt(pIndex);
        }
        map.addAttribute("articlesData", fileService.getArticles(page, pageSize));
        return isPjax ? "articles" : "index";
    }

    @GetMapping("/articles/o/{slug}")
    @LogOperatingFun(value = "独立页面", logType = LogOperation.Type.ARTICLE)
    public String alonePage(HttpServletRequest request, @PathVariable String slug, ModelMap map) {
        return articlePage(request, slug, map);
    }

    @GetMapping("/articles/s/{slug}")
    @LogOperatingFun(value = "文章", logType = LogOperation.Type.ARTICLE)
    public String article(HttpServletRequest request, @PathVariable String slug, ModelMap map) {
        return articlePage(request, slug, map);
    }

    private String articlePage(HttpServletRequest request, String slug, ModelMap map) {
        boolean isPjax = pjaxMap(request, map, "article");
        ArticleVO articleVO = fileService.getMarkDownContentBySlug(slug);
        if (articleVO == null) {
            return notFind(request, map);
        }
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("consumerId".equals(cookie.getName())) {
                    articleVO.setEditable(true);
                }
            }
        }
        map.addAttribute("markdown", articleVO);
        map.addAttribute("titleName", articleVO.getName());
        return isPjax ? "article" : "index";
    }

    @GetMapping("/articles/categories")
    @LogOperatingFun(value = "文章分类", logType = LogOperation.Type.ARTICLE)
    public String categories(HttpServletRequest request, ModelMap map) {
        boolean isPjax = pjaxMap(request, map, "categories");
        map.addAttribute("titleName", "分类");
        map.addAttribute("categories", categoryService.list(null, null));
        return isPjax ? "categories" : "index";
    }

    @GetMapping("/articles/archives")
    @LogOperatingFun(value = "文章归档", logType = LogOperation.Type.ARTICLE)
    public String archives(HttpServletRequest request, ModelMap map) {
        boolean isPjax = pjaxMap(request, map, "archives");
        map.addAttribute("titleName", "归档");
        int page = 1, pageSize = 100;
        String pIndex = request.getParameter("page");
        if (!StringUtils.isEmpty(pIndex)) {
            page = Integer.parseInt(pIndex);
        }
        map.addAttribute("articlesData", fileService.getArchives(page, pageSize));
        return isPjax ? "archives" : "index";
    }

    @GetMapping("/articles/categories/{categorySlugName}")
    @LogOperatingFun(value = "文章分类", logType = LogOperation.Type.ARTICLE)
    public String getCategoryByName(HttpServletRequest request, ModelMap map, @PathVariable String categorySlugName) {
        boolean isPjax = pjaxMap(request, map, "articles-query");
        if (StringUtils.isEmpty(categorySlugName)) {
            return notFind(request, map);
        }
        String categoryId = null;
        if (!StringUtils.isEmpty(categorySlugName)) {
            CategoryDO categoryDO = categoryService.getCategoryInfoBySlug(null, categorySlugName);
            if (categoryDO == null) {
                return notFind(request, map);
            }
            ArticlesQueryVO query = categoryDO.toArticlesQuery();
            map.addAttribute("titleName", query.getName());
            map.addAttribute("query", query);
            categoryId = categoryDO.getId();
        }
        int page = 1, pageSize = 10;
        String pIndex = request.getParameter("page");
        if (!StringUtils.isEmpty(pIndex)) {
            page = Integer.parseInt(pIndex);
        }
        map.addAttribute("articlesData", fileService.getArticlesByCategoryId(page, pageSize, categoryId));
        return isPjax ? "articles-query" : "index";
    }

    @GetMapping("/articles/tags")
    @LogOperatingFun(value = "文章标签", logType = LogOperation.Type.ARTICLE)
    public String tags(HttpServletRequest request, ModelMap map) {
        boolean isPjax = pjaxMap(request, map, "tags");
        map.addAttribute("titleName", "标签");
        map.addAttribute("tags", tagService.listTagsOfArticle());
        return isPjax ? "tags" : "index";
    }

    @GetMapping("/articles/tags/{tagSlugName}")
    @LogOperatingFun(value = "文章标签", logType = LogOperation.Type.ARTICLE)
    public String getTagByName(HttpServletRequest request, ModelMap map, @PathVariable String tagSlugName) {
        boolean isPjax = pjaxMap(request, map, "articles-query");
        if (StringUtils.isEmpty(tagSlugName)) {
            return notFind(request, map);
        }
        String tagId = null;
        if (!StringUtils.isEmpty(tagSlugName)) {
            TagDO tag = tagService.getTagInfoBySlug(null, tagSlugName);
            if (tag == null) {
                return notFind(request, map);
            }
            ArticlesQueryVO query = tag.toArticlesQuery();
            map.addAttribute("titleName", query.getName());
            map.addAttribute("query", query);
            tagId = tag.getId();
        }
        int page = 1, pageSize = 10;
        String pIndex = request.getParameter("page");
        if (!StringUtils.isEmpty(pIndex)) {
            page = Integer.parseInt(pIndex);
        }
        map.addAttribute("articlesData", fileService.getArticlesByTagId(page, pageSize, tagId));
        return isPjax ? "articles-query" : "index";
    }

    @GetMapping("/articles/search")
    @LogOperatingFun(value = "文章搜索", logType = LogOperation.Type.ARTICLE)
    public String search(HttpServletRequest request, ModelMap map, @RequestParam String keyword) {
        boolean isPjax = pjaxMap(request, map, "articles-query");
        int page = 1, pageSize = 10;
        String pIndex = request.getParameter("page");
        if (!StringUtils.isEmpty(pIndex)) {
            page = Integer.parseInt(pIndex);
        }
        ArticlesQueryVO query = new ArticlesQueryVO();
        Page<List<MarkdownVO>> articles = fileService.getArticlesByKeyword(page, pageSize, keyword);
        if (!articles.isEmpty()) {
            MarkdownVO markdownVO = articles.getData().get(0);
            query.setBackground(markdownVO.getCover());
        }
        query.setName("包含关键字 " + keyword + " 的文章");
        map.addAttribute("titleName", query.getName());
        map.addAttribute("query", query);
        map.addAttribute("articlesData", articles);
        return isPjax ? "articles-query" : "index";
    }

    @GetMapping("/articles/author/{username}")
    @LogOperatingFun(value = "文章作者", logType = LogOperation.Type.ARTICLE)
    public String author(HttpServletRequest request, ModelMap map, @PathVariable String username) {
        boolean isPjax = pjaxMap(request, map, "articles-query");
        String userId = userService.getUserIdByShowName(username);
        if (StringUtils.isEmpty(userId)) {
            return notFind(request, map);
        }
        int page = 1, pageSize = 10;
        String pIndex = request.getParameter("page");
        if (!StringUtils.isEmpty(pIndex)) {
            page = Integer.parseInt(pIndex);
        }
        ArticlesQueryVO query = new ArticlesQueryVO();
        Page<List<MarkdownVO>> articles = fileService.getArticlesByAuthor(page, pageSize, userId);
        if (!articles.isEmpty()) {
            MarkdownVO markdownVO = articles.getData().get(0);
            query.setBackground(markdownVO.getCover());
        }
        query.setName(username + " 发布的文章");
        map.addAttribute("titleName", query.getName());
        map.addAttribute("query", query);
        map.addAttribute("articlesData", articles);
        return isPjax ? "articles-query" : "index";
    }

    private WebsiteSettingDTO getSetting(HttpServletRequest request, ModelMap map) {
        WebsiteSettingDTO websiteSettingDTO = settingService.getWebsiteSetting();
        setOperatingButtonList(websiteSettingDTO);
        List<MarkdownVO> markdownVOList = fileService.getAlonePages();
        map.addAttribute("alonePages", markdownVOList);
        map.addAttribute("setting", websiteSettingDTO);
        int alonePageShowIndex = 4 - websiteSettingDTO.getAlonePages().size();
        if (markdownVOList.size() > alonePageShowIndex) {
            markdownVOList = markdownVOList.subList(0, alonePageShowIndex);
        }
        map.addAttribute("showAlonePages", markdownVOList);
        map.addAttribute("darkTheme", darkTheme(request));
        return websiteSettingDTO;
    }

    /***
     * 解析设置里的文本为操作按钮列表
     * @param websiteSettingDTO userSettingDTO
     */
    private void setOperatingButtonList(WebsiteSettingDTO websiteSettingDTO) {
        if (websiteSettingDTO != null && !StringUtils.isEmpty(websiteSettingDTO.getOperatingButtons())) {
            String operatingButtons = websiteSettingDTO.getOperatingButtons();
            List<WebsiteSettingDTO.OperatingButton> operatingButtonList = new ArrayList<>();
            for (String button : operatingButtons.split("[\\n]")) {
                WebsiteSettingDTO.OperatingButton operatingButton = new WebsiteSettingDTO.OperatingButton();
                int splitIndex = button.indexOf(":");
                String label = button.substring(0, splitIndex);
                String title = ReUtil.getGroup0("[^><]+(?=<\\/i>)", label);
                if (StringUtils.isEmpty(title)) {
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
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("jmal-theme".equals(cookie.getName())) {
                    if ("dark".equals(cookie.getValue())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /***
     * 判断请求是否为pjax
     * @param request HttpServletRequest
     * @return boolean
     */
    private boolean isPjax(HttpServletRequest request) {
        boolean pjax = false;
        if (X_PJAX_TRUE.equals(request.getHeader(X_PJAX))) {
            pjax = true;
        }
        return pjax;
    }

    private boolean pjaxMap(HttpServletRequest request, ModelMap map, String viewName) {
        boolean isPjax = isPjax(request);
        if(!isPjax) {
            getSetting(request, map);
        } else {
            map.addAttribute("setting", settingService.getWebsiteSetting());
        }
        map.addAttribute("mark", viewName);
        return isPjax;
    }

}



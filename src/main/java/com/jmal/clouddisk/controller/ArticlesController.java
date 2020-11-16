package com.jmal.clouddisk.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;

/**
 * @author jmal
 * @Description 文章首页
 * @Date 2020/11/16 5:41 下午
 */
@Controller
public class ArticlesController {
    @RequestMapping("/")
    public String index(HttpServletRequest request, ModelMap map){
        int projectId = 0, pageIndex = 1, pageSize = 10;
        String pId = request.getParameter("projectId");
        String pIndex = request.getParameter("pageIndex");
        String pSize = request.getParameter("pageSize");
        if(!StringUtils.isEmpty(pId)){
            projectId = Integer.parseInt(pId);
        }
        if(!StringUtils.isEmpty(pIndex)){
            pageIndex = Integer.parseInt(pIndex);
        }
        if(!StringUtils.isEmpty(pSize)){
            pageSize = Integer.parseInt(pSize);
        }
        return "index";
    }
}

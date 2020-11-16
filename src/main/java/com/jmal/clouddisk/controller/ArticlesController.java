package com.jmal.clouddisk.controller;

import cn.hutool.core.lang.Console;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.model.UserSettingDTO;
import com.jmal.clouddisk.service.impl.SettingService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @GetMapping("/articles")
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
        UserSettingDTO userSettingDTO = settingService.getWebsiteSetting();
        if(userSettingDTO != null && !StringUtils.isEmpty(userSettingDTO.getOperatingButtons())){
            String operatingButtons = userSettingDTO.getOperatingButtons();
        }
        map.addAttribute("setting", userSettingDTO);
        return "index";
    }

    public static void main(String[] args) {
        String operatingButtons = "<i class=\"fab fa-github\">ffs</i>:https://github.com/jamebal\n<i class=\"fa fa-cog\"></i>:/setting/website/manager-blog";
        for (String button : operatingButtons.split("[\\n]")) {
            UserSettingDTO.OperatingButton operatingButton = new UserSettingDTO.OperatingButton();
            int splitIndex = button.indexOf(":");
            String ihtml = button.substring(0, splitIndex);
            Console.log("ihtml:", ihtml);
            // 获取标签里的内容
            Pattern regLabel = Pattern.compile("<i[^<>]*?\\\\s(.*?)['\\\"]?\\\\s.*?>");
            Matcher matcher = regLabel.matcher(ihtml);
            String title = matcher.group();
            operatingButton.setTitle(title);
            // 去掉标签里的内容
            operatingButton.setFontHtml(ihtml.replaceAll(regLabel.toString(), ""));
            operatingButton.setUrl(button.substring(splitIndex + 1, button.length()));
            Console.log(operatingButton);
        }
    }
}



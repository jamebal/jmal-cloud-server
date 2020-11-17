package com.jmal.clouddisk.controller;

import cn.hutool.core.util.ReUtil;
import com.jmal.clouddisk.model.UserSettingDTO;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.service.impl.SettingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

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
    private IFileService fileService;

    @GetMapping("/articles")
    public String index(HttpServletRequest request, ModelMap map){
        int pageIndex = 1, pageSize = 10;
        String pIndex = request.getParameter("pageIndex");
        String pSize = request.getParameter("pageSize");
        if(!StringUtils.isEmpty(pIndex)){
            pageIndex = Integer.parseInt(pIndex);
        }
        if(!StringUtils.isEmpty(pSize)){
            pageSize = Integer.parseInt(pSize);
        }
        UserSettingDTO userSettingDTO = settingService.getWebsiteSetting();
        setOperatingButtonList(userSettingDTO);
        map.addAttribute("setting", userSettingDTO);
        map.addAttribute("articlesData", fileService.getMarkDownContent(null, pageIndex, pageSize));
        return "index";
    }

    private void setOperatingButtonList(UserSettingDTO userSettingDTO) {
        if(userSettingDTO != null && !StringUtils.isEmpty(userSettingDTO.getOperatingButtons())){
            String operatingButtons = userSettingDTO.getOperatingButtons();
            List<UserSettingDTO.OperatingButton> operatingButtonList = new ArrayList<>();
            for (String button : operatingButtons.split("[\\n]")) {
                UserSettingDTO.OperatingButton operatingButton = new UserSettingDTO.OperatingButton();
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
            userSettingDTO.setOperatingButtonList(operatingButtonList);
        }
    }

}



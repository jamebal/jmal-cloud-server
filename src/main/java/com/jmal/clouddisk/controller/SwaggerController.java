package com.jmal.clouddisk.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author jmal
 * @Description SwaggerController
 * @date 2021/12/2 17:25
 */
@Controller
public class SwaggerController {

    @GetMapping("/public/v2/api-docs")
    public void  swagger(HttpServletRequest request, HttpServletResponse response){
        String url = "/v2/api-docs";
        System.out.println(url);
        try {
            request.getRequestDispatcher(url).forward(request,response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

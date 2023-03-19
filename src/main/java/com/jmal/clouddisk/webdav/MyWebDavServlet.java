package com.jmal.clouddisk.webdav;

import cn.hutool.core.lang.Console;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.servlets.WebdavServlet;

import java.io.IOException;
import java.util.Enumeration;

public class MyWebDavServlet extends WebdavServlet {

    public MyWebDavServlet() {
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.service(req, resp);
    }

    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        Console.log("doOptions request", request.getRequestURI());
        Enumeration<String> requestHeaderNames = request.getHeaderNames();
        while (requestHeaderNames.hasMoreElements()) {
            String headerName = requestHeaderNames.nextElement();
            Console.log(headerName, request.getHeader(headerName));
        }
        super.doOptions(request, response);
        Console.log("doOptions response", response.getStatus());
        for (String headerName : response.getHeaderNames()) {
            Console.log(headerName, response.getHeader(headerName));
        }
    }

    @Override
    protected void doPropfind(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPropfind(req, resp);
    }
}

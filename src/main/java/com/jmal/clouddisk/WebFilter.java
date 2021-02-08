package com.jmal.clouddisk;


import cn.hutool.core.lang.Console;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.alibaba.fastjson.JSONObject;
import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.LogOperation;
import io.milton.config.HttpManagerBuilder;
import io.milton.http.HttpManager;
import io.milton.http.Request;
import io.milton.http.ResourceFactory;
import io.milton.http.Response;
import io.milton.http.annotated.AnnotationResourceFactory;
import io.milton.http.template.JspViewResolver;
import io.milton.http.template.ViewResolver;
import io.milton.servlet.MiltonServlet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * @author jmal
 * @Description 路径过滤器
 * @Date 2020/10/20 4:12 下午
 */
@Component
public class WebFilter implements Filter {

    private static final String API = "/api";
    private static final Pattern COMPILE = Pattern.compile(API);

    @Autowired
    private FileProperties fileProperties;

    @Autowired
    private HttpManagerBuilder httpManagerBuilder;

    private HttpManager httpManager;

    @Override
    public void init(FilterConfig filterConfig) {
        ResourceFactory rf = httpManagerBuilder.getMainResourceFactory();
        if (rf instanceof AnnotationResourceFactory) {
            AnnotationResourceFactory arf = (AnnotationResourceFactory) rf;
            if (arf.getViewResolver() == null) {
                ViewResolver viewResolver = new JspViewResolver(filterConfig.getServletContext());
                arf.setViewResolver(viewResolver);
            }
        }
        this.httpManager = httpManagerBuilder.buildHttpManager();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        // doMiltonProcessing((HttpServletRequest) request, (HttpServletResponse) response);
        HttpServletRequest httpRequest = (HttpServletRequest)request;
        String uri = httpRequest.getRequestURI();
        if(uri.startsWith(API)) {
            uri = COMPILE.matcher(uri).replaceFirst("");
            httpRequest.getRequestDispatcher(uri).forward(request,response);
        }
        // 以/webDAV 开头的走webDAV协议
        if(uri.startsWith(fileProperties.getWebDavPrefixPath())){
            doMiltonProcessing((HttpServletRequest) request, (HttpServletResponse) response);
            return;
        }
        chain.doFilter(request,response);
    }

    /***
     * webDAV 的请求
     * @param req HttpServletRequest
     * @param resp HttpServletResponse
     */
    @LogOperatingFun(value = "WebDAV请求", logType = LogOperation.Type.WEBDAV)
    public void doMiltonProcessing(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            MiltonServlet.setThreadlocals(req, resp);
            Request request = new io.milton.servlet.ServletRequest(req, req.getServletContext());
            Response response = new io.milton.servlet.ServletResponse(resp);
            UserAgent userAgent = UserAgentUtil.parse(request.getUserAgentHeader());
            if(!userAgent.getBrowser().isUnknown()){
                notAllowBrowser(resp);
                return;
            }
            // TODO 暂不支持LOCK和UNLOCK
            if(Request.Method.LOCK.toString().equals(req.getMethod())){
                response.setStatus(Response.Status.SC_METHOD_NOT_ALLOWED);
                return;
            }
            httpManager.process(request, response);
        } finally {
            MiltonServlet.clearThreadlocals();
            resp.flushBuffer();
        }
    }

    /***
     * 不允许浏览器访问webDAV
     */
    private void notAllowBrowser(HttpServletResponse resp) throws IOException {
        resp.setHeader("Content-type", "text/html;charset=UTF-8");
        resp.getWriter().print("<p>This is the WebDAV interface. It can only be accessed by WebDAV clients.</p></br>");
        resp.getWriter().println("Windows : <a href='https://www.raidrive.com/'>RaiDrive</a></br>");
        resp.getWriter().println("Mac OS : Finder</br>");
        resp.getWriter().println("Android : <a href='https://www.coolapk.com/apk/com.estrongs.android.pop'>ES文件浏览器</a></br>");
        resp.getWriter().println("iOS : <a href='https://apps.apple.com/cn/app/documents-by-readdle/id364901807'>Documents</a></br>");
        resp.getWriter().println("Coming soon : Jmal Cloud Client.</br>");
        resp.getWriter().close();
    }

    @Override
    public void destroy() {
        if (httpManager != null) {
            httpManager.shutdown();
        }
    }

}

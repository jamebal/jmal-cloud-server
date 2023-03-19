package com.jmal.clouddisk;


import cn.hutool.core.lang.Console;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.service.impl.LogService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.webdav.MySimpleSecurityManager;
import io.milton.config.HttpManagerBuilder;
import io.milton.http.HttpManager;
import io.milton.http.Request;
import io.milton.http.Response;
import io.milton.servlet.MiltonServlet;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.regex.Pattern;

/**
 * @author jmal
 * @Description 路径过滤器
 * @Date 2020/10/20 4:12 下午
 */
@Slf4j
@Component
@Lazy
public class WebFilter implements Filter {

    public static final String API = "/api";
    public static final Pattern COMPILE = Pattern.compile(API);

    @Autowired
    private FileProperties fileProperties;

    @Autowired
    private HttpManagerBuilder httpManagerBuilder;

    @Autowired
    private MySimpleSecurityManager mySimpleSecurityManager;

    @Autowired
    private LogService logService;

    private HttpManager httpManager;

    // @Override
    // public void init(FilterConfig filterConfig) {
    //      ResourceFactory rf = httpManagerBuilder.getMainResourceFactory();
    //      if (rf instanceof AnnotationResourceFactory arf) {
    //          if (arf.getViewResolver() == null) {
    //              ViewResolver viewResolver = new JspViewResolver(filterConfig.getServletContext());
    //              arf.setViewResolver(viewResolver);
    //          }
    //      }
    //      log.info("WEBDAV 服务启动, contextPath: {}", fileProperties.getWebDavPrefixPath());
    //      this.httpManager = httpManagerBuilder.buildHttpManager();
    // }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpServletResponse = (HttpServletResponse) response;
        String uri = httpRequest.getRequestURI();
        if (uri.startsWith(API)) {
            uri = COMPILE.matcher(uri).replaceFirst("");
            httpRequest.getRequestDispatcher(uri).forward(request, response);
            return;
        }
        // 以/webDAV 开头的走webDAV协议
        // if (uri.startsWith(fileProperties.getWebDavPrefixPath())) {
        //     long time = System.currentTimeMillis();
        //     ResponseResult<Object> result = doMiltonProcessing(httpRequest, httpServletResponse);
        //     recordLog(httpRequest, httpServletResponse, time, result);
        //     return;
        // }
        CaffeineUtil.setLastAccessTimeCache();
        chain.doFilter(request, response);
    }

    /***
     * 记录webDAV请求日志
     */
    private void recordLog(HttpServletRequest request, HttpServletResponse response, long time, ResponseResult<Object> result) {
        if (MySimpleSecurityManager.NO_LOG_METHODS.contains(request.getMethod())){
            return;
        }
        LogOperation logOperation = new LogOperation();
        logOperation.setTime(System.currentTimeMillis() - time);
        String username = mySimpleSecurityManager.getUsernameByUri(request.getRequestURI());
        logOperation.setUsername(username);
        logOperation.setOperationModule("WEBDAV");
        logOperation.setOperationFun("WebDAV请求");
        logOperation.setType(LogOperation.Type.WEBDAV.name());
        logService.addLogBefore(logOperation, result, request, response);
    }

    /***
     * webDAV 的请求
     * @param req HttpServletRequest
     * @param resp HttpServletResponse
     */
    @LogOperatingFun(value = "WebDAV请求", logType = LogOperation.Type.WEBDAV)
    public ResponseResult<Object> doMiltonProcessing(HttpServletRequest req, HttpServletResponse resp) throws IOException {
         try {
             MiltonServlet.setThreadlocals( req, resp);
             Request request = new io.milton.servlet.ServletRequest(req, req.getServletContext());
             Response response = new io.milton.servlet.ServletResponse(resp);
             UserAgent userAgent = UserAgentUtil.parse(request.getUserAgentHeader());
             if (!userAgent.getBrowser().isUnknown()) {
                 notAllowBrowser(resp);
                 return ResultUtil.warning("webDAV 不支持浏览器访问");
             }
             // 暂不支持LOCK和UNLOCK
             if (Request.Method.LOCK.toString().equals(req.getMethod())) {
                 response.setStatus(Response.Status.SC_METHOD_NOT_ALLOWED);
                 return ResultUtil.warning("webDAV 暂不支持LOCK和UNLOCK请求");
             }
             if (Request.Method.OPTIONS.toString().equals(req.getMethod())) {
                 Console.log("---------- request start -----------");
                 Console.log(request.getHeaders());
                 Console.log("---------- request end -----------");
             }
             httpManager.process(request, response);
             if (Request.Method.OPTIONS.toString().equals(req.getMethod())) {
                 Console.log("---------- response start -----------");
                 Console.log(response.getStatus(), response.getHeaders());
                 Console.log("---------- response end -----------");
             }
         } finally {
             MiltonServlet.clearThreadlocals();
             resp.flushBuffer();
         }
        return null;
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

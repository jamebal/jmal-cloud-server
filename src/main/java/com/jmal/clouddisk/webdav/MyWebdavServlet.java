package com.jmal.clouddisk.webdav;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.URLUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jmal.clouddisk.oss.OssInputStream;
import com.jmal.clouddisk.webdav.resource.OssFileResource;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.WebResource;
import org.apache.catalina.servlets.WebdavServlet;
import org.apache.tomcat.util.http.parser.Ranges;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;

/**
 * @author jmal
 * @Description WebdavServlet
 * @date 2023/3/27 09:35
 */
@Component
@Slf4j
public class MyWebdavServlet extends WebdavServlet {

    private static final Cache<String, Long> REQUEST_URI_GET_MAP = Caffeine.newBuilder().expireAfterWrite(5L, TimeUnit.SECONDS).build();

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String method = request.getMethod();
        // 过滤掉mac Finder "._" 文件请求
        if (filterMac(request, response, method)) return;
        // 过滤掉过于频繁的GET请求
        if (filterTooManyRequest(request, response, method)) return;
        long time = System.currentTimeMillis();
        log.info("request: {}, requestId: {}, uri: {} {}", "---", time, method, URLUtil.decode(request.getRequestURI()));
        super.service(request, response);
        log.info("response: {}, requestId: {}, uri: {} {}", response.getStatus(), time, method, URLUtil.decode(request.getRequestURI()));
    }

    /**
     * 过滤掉mac Finder "._" 文件请求
     */
    private static boolean filterMac(HttpServletRequest request, HttpServletResponse response, String method) throws IOException {
        if (method.equals(WebdavMethod.PROPFIND.getCode()) || method.equals(WebdavMethod.PUT.getCode())) {
            Path path = Paths.get(request.getRequestURI());
            // 过滤掉mac Finder "._" 文件请求
            if (path.getFileName().toString().startsWith("._")) {
                response.sendError(404);
                return true;
            }
        }
        return false;
    }

    /**
     * 过滤掉过于频繁的GET请求, 同一文件2秒内相同GET的请求
     */
    private static boolean filterTooManyRequest(HttpServletRequest request, HttpServletResponse response, String method) throws IOException {
        String uri = request.getRequestURI();
        if (method.equals(WebdavMethod.GET.getCode())) {
            if (!CharSequenceUtil.isBlank(request.getHeader("If-Range")) || !CharSequenceUtil.isBlank(request.getHeader("Range"))) {
                response.sendError(423);
                return true;
            }
            Long time = REQUEST_URI_GET_MAP.getIfPresent(uri);
            if (time != null && (System.currentTimeMillis() - time) < 4000) {
                response.sendError(423);
                return true;
            }
            REQUEST_URI_GET_MAP.put(uri, System.currentTimeMillis());
        }
        return false;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        log.info("GET request: uri: {} {}", "---", URLUtil.decode(request.getRequestURI()));
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            Console.log(name, request.getHeader(name));
        }
        super.doGet(request, response);
        log.info("GET response: {} uri: {} {}", response.getStatus(), "---", URLUtil.decode(request.getRequestURI()));
        if (response.getStatus() == 206) {
            response.setStatus(200);
            log.info("GET response: {} uri: {} {}", response.getStatus(), "---", URLUtil.decode(request.getRequestURI()));
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String uri = req.getRequestURI();
        // 禁止删除oss根目录
        if (uri.endsWith("/jmal/aliyunoss/")) {
            sendNotAllowed(req, resp);
            return;
        }
        super.doDelete(req, resp);
    }


}

package com.jmal.clouddisk.webdav;

import cn.hutool.core.date.DatePattern;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Console;
import cn.hutool.core.util.URLUtil;
import cn.hutool.http.server.HttpServerRequest;
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
import org.springframework.http.server.ServletServerHttpRequest;
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
    protected void copy(WebResource resource, long length, ServletOutputStream outStream, Ranges.Entry range) throws IOException {
        IOException exception;
        InputStream resourceInputStream = resource.getInputStream();
        OssFileResource ossFileResource = null;
        if (resourceInputStream instanceof OssInputStream ossInputStream) {
            ossFileResource = ossInputStream.getOssFileResource();
        }
        InputStream inStream = new BufferedInputStream(resourceInputStream, input);
        Console.log(DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_PATTERN), "copy-1", ossFileResource != null ? ossFileResource.getFileInfo() : "");
        exception = copyRange(inStream, outStream, getStart(range, length), getEnd(range, length));
        Console.log(DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_PATTERN),"copy-1.1", ossFileResource != null ? ossFileResource.getFileInfo() : "");
        inStream.close();
        if (ossFileResource != null) {
            ossFileResource.closeObject();
        }
        if (exception != null) {
            Console.log("exception1", exception, ossFileResource, ossFileResource != null ? ossFileResource.getFileInfo() : "");
            throw exception;
        }
    }

    @Override
    protected void copy(InputStream is, ServletOutputStream outStream) throws IOException {
        IOException exception;
        OssFileResource ossFileResource = null;
        if (is instanceof OssInputStream ossInputStream) {
            ossFileResource = ossInputStream.getOssFileResource();
        }
        InputStream inStream = new BufferedInputStream(is, input);
        Console.log(DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_PATTERN),"copy-2", ossFileResource != null ? ossFileResource.getFileInfo() : "");
        exception = copyRange(inStream, outStream);
        Console.log(DateUtil.format(new Date(), DatePattern.NORM_DATETIME_MS_PATTERN),"copy-2.1", ossFileResource != null ? ossFileResource.getFileInfo() : "");
        inStream.close();
        if (ossFileResource != null) {
            ossFileResource.closeObject();
        }
        if (exception != null) {
            Console.log("exception2", exception, ossFileResource, ossFileResource != null ? ossFileResource.getFileInfo() : "");
            throw exception;
        }
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

    @Override
    protected void copy(InputStream is, PrintWriter writer, String encoding) throws IOException {
        Console.log("copy3");
        super.copy(is, writer, encoding);
    }

    @Override
    protected void copy(WebResource resource, long length, ServletOutputStream ostream, Ranges ranges, String contentType) throws IOException {
        Console.log("copy4");
        super.copy(resource, length, ostream, ranges, contentType);
    }

    private static long getStart(Ranges.Entry range, long length) {
        long start = range.getStart();
        if (start == -1 ) {
            long end = range.getEnd();
            // If there is no start, then the start is based on the end
            if (end >= length) {
                return 0;
            } else {
                return length - end;
            }
        } else {
            return start;
        }
    }

    private static long getEnd(Ranges.Entry range, long length) {
        long end = range.getEnd();
        if (range.getStart() == -1 || end == -1 || end >= length) {
            return length - 1;
        } else {
            return end;
        }
    }


}

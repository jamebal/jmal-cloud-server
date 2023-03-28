package com.jmal.clouddisk.webdav;

import cn.hutool.core.lang.Console;
import cn.hutool.core.util.BooleanUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jmal.clouddisk.webdav.resource.AliyunOSSFileResource;
import com.jmal.clouddisk.webdav.resource.OSSInputStream;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.WebResource;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.servlets.WebdavServlet;
import org.apache.tomcat.util.http.parser.Ranges;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * @author jmal
 * @Description WebdavServlet
 * @date 2023/3/27 09:35
 */
@Component
public class MyWebdavServlet extends WebdavServlet {

    private static final Cache<String, Long> REQUEST_URI_MAP = Caffeine.newBuilder().expireAfterWrite(3L, TimeUnit.SECONDS).build();

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String method = request.getMethod();
        // 过滤掉mac Finder "._" 文件请求
        if (filterMac(request, response, method)) return;
        // 过滤掉过于频繁的GET请求
        if (filterTooManyRequest(request, response, method)) return;
        super.service(request, response);
    }

    /**
     * 过滤掉mac Finder "._" 文件请求
     */
    private static boolean filterMac(HttpServletRequest request, HttpServletResponse response, String method) throws IOException {
        if (method.equals(WebdavMethod.PROPFIND.getCode())) {
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
            Long time = REQUEST_URI_MAP.getIfPresent(uri);
            if (time != null && (System.currentTimeMillis() - time) < 2000) {
                response.sendError(423);
                return true;
            }
            REQUEST_URI_MAP.put(uri, System.currentTimeMillis());
        }
        return false;
    }

    @Override
    protected void copy(WebResource resource, long length, ServletOutputStream outStream, Ranges.Entry range) throws IOException {
        IOException exception = null;
        InputStream resourceInputStream = resource.getInputStream();
        AliyunOSSFileResource aliyunOSSFileResource = null;
        if (resourceInputStream instanceof OSSInputStream ossInputStream) {
            aliyunOSSFileResource = ossInputStream.getOssFileResource();
            if (BooleanUtil.isTrue(aliyunOSSFileResource.lock2.get())) {
                Console.error("lock2");
                exception = new ClientAbortException("resource is lock");
            }
            Console.log("copy1.1", aliyunOSSFileResource.getOSSObject());
        }
        Console.log("copy1.2", resource.getName(), exception);
        if (exception != null) {
            throw exception;
        }
        InputStream inStream = new BufferedInputStream(resourceInputStream, input);
        exception = copyRange(inStream, outStream, getStart(range, length), getEnd(range, length));
        inStream.close();
        if (aliyunOSSFileResource != null) {
            Console.log("closeObject1");
            aliyunOSSFileResource.closeObject();
        }
        Console.log("copy1.3", resource.getName(), exception);
        if (exception != null) {
            throw exception;
        }
    }

    @Override
    protected void copy(InputStream is, ServletOutputStream outStream) throws IOException {
        IOException exception;
        AliyunOSSFileResource aliyunOSSFileResource = null;
        if (is instanceof OSSInputStream ossInputStream) {
            aliyunOSSFileResource = ossInputStream.getOssFileResource();
            aliyunOSSFileResource.lock2.set(true);
        }
        Console.log("copy2", aliyunOSSFileResource);
        InputStream inStream = new BufferedInputStream(is, input);
        exception = copyRange(inStream, outStream);
        inStream.close();
        if (aliyunOSSFileResource != null) {
            Console.log("closeObject2");
            aliyunOSSFileResource.closeObject();
            aliyunOSSFileResource.lock2.set(false);
        }
        if (exception != null) {
            throw exception;
        }
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

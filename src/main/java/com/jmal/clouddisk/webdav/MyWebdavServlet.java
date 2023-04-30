package com.jmal.clouddisk.webdav;

import cn.hutool.core.lang.Console;
import cn.hutool.core.text.CharSequenceUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jmal.clouddisk.oss.AbstractOssObject;
import com.jmal.clouddisk.oss.IOssService;
import com.jmal.clouddisk.oss.OssInputStream;
import com.jmal.clouddisk.util.CaffeineUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.WebResource;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.servlets.WebdavServlet;
import org.apache.tomcat.util.http.parser.Ranges;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * @author jmal
 * @Description WebdavServlet
 * @date 2023/3/27 09:35
 */
@Component
@Slf4j
public class MyWebdavServlet extends WebdavServlet {

    public static final String PATH_DELIMITER = "/";

    private static final Cache<String, Long> REQUEST_URI_GET_MAP = Caffeine.newBuilder().expireAfterWrite(3L, TimeUnit.SECONDS).build();

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String method = request.getMethod();
        // 过滤掉mac Finder "._" 文件请求
        if (filterMac(request, response, method)) return;
        // 过滤掉过于频繁的GET请求, 只针对 oss
        if (filterTooManyRequest(request, response, method)) return;
        super.service(request, response);
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
     * 过滤掉过于频繁的GET请求, 同一文件1秒内相同GET的请求
     * 目前只针对oss
     */
    private static boolean filterTooManyRequest(HttpServletRequest request, HttpServletResponse response, String method) throws IOException {
        String uri = request.getRequestURI();
        Path uriPath = Paths.get(uri);
        if (uriPath.getNameCount() > 1) {
            String ossPath = CaffeineUtil.getOssPath(uriPath.subpath(1, uriPath.getNameCount()));
            if (ossPath == null) {
                return false;
            }
        }
        if (method.equals(WebdavMethod.GET.getCode())) {
            if (!CharSequenceUtil.isBlank(request.getHeader("If-Range")) || !CharSequenceUtil.isBlank(request.getHeader("Range"))) {
                response.sendError(423);
                return true;
            }
            Long time = REQUEST_URI_GET_MAP.getIfPresent(uri);
            if (time != null && (System.currentTimeMillis() - time) < 1000) {
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
        AbstractOssObject ossObject = null;
        if (resourceInputStream instanceof OssInputStream ossInputStream) {
            ossObject = ossInputStream.getAbstractOssObject();
        }
        long rangeStart = getStart(range, length);
        long rangeEnd = getEnd(range, length);

        if (ossObject != null) {
            IOssService ossService = ossObject.getOssService();
            String objectName = ossObject.getKey();
            ossObject.closeObject();
            AbstractOssObject rangeObject = ossService.getAbstractOssObject(objectName, rangeStart, rangeEnd);
            copy(outStream, rangeObject, rangeObject.getInputStream());
        } else {
            InputStream inputStream = new BufferedInputStream(resourceInputStream, this.input);
            exception = this.copyRange(inputStream, outStream, rangeStart, rangeEnd);
            inputStream.close();
            if (exception != null) {
                throw exception;
            }
        }

    }

    private static long getStart(Ranges.Entry range, long length) {
        long start = range.getStart();
        if (start == -1L) {
            long end = range.getEnd();
            return end >= length ? 0L : length - end;
        } else {
            return start;
        }
    }

    private static long getEnd(Ranges.Entry range, long length) {
        long end = range.getEnd();
        return range.getStart() != -1L && end != -1L && end < length ? end : length - 1L;
    }

    @Override
    protected void copy(InputStream is, ServletOutputStream outStream) throws IOException {
        AbstractOssObject abstractOssObject = null;
        if (is instanceof OssInputStream ossInputStream) {
            abstractOssObject = ossInputStream.getAbstractOssObject();
        }
        InputStream inStream = new BufferedInputStream(is, input);
        copy(outStream, abstractOssObject, inStream);
    }

    private void copy(ServletOutputStream outStream, AbstractOssObject abstractOssObject, InputStream inStream) throws IOException {
        IOException exception;
        exception = copyRange(inStream, outStream);
        if (abstractOssObject != null) {
            try {
                abstractOssObject.closeObject();
            } catch (IOException e) {
                Console.error(e.getMessage());
                exception = new ClientAbortException("Broken pipe");
            } finally {
                try {
                    inStream.close();
                } catch (IOException e) {
                    exception = new ClientAbortException("Broken pipe");
                }
            }
        } else {
            inStream.close();
        }
        if (exception != null) {
            throw exception;
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String uri = req.getRequestURI();
        Path uriPath = Paths.get(uri);
        if (uriPath.getNameCount() > 1) {
            String ossPath = CaffeineUtil.getOssPath(uriPath.subpath(1, uriPath.getNameCount()));
            if (ossPath != null && uri.endsWith(ossPath + PATH_DELIMITER)) {
                // 禁止删除oss根目录
                sendNotAllowed(req, resp);
                return;
            }
        }
        super.doDelete(req, resp);
    }

    public static String getPathDelimiter(String username, String folderName) {
        return MyWebdavServlet.PATH_DELIMITER + Paths.get(username, folderName);
    }

}

package com.jmal.clouddisk.webdav;

import cn.hutool.core.lang.Console;
import cn.hutool.core.text.CharSequenceUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.oss.AbstractOssObject;
import com.jmal.clouddisk.oss.IOssService;
import com.jmal.clouddisk.oss.OssConfigService;
import com.jmal.clouddisk.oss.OssInputStream;
import com.jmal.clouddisk.oss.web.WebOssService;
import com.jmal.clouddisk.service.IFileService;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.FileNameUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.WebResource;
import org.apache.tomcat.util.http.parser.Ranges;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.File;
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
@RequiredArgsConstructor
public class MyWebdavServlet extends WebdavServlet {


    private final transient FileProperties fileProperties;

    private final transient ObjectProvider<IFileService> fileServiceObjectProvider;

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
     * 目前只针对oss 和 mac
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
        // MAC & OSS & GET
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        if (!CharSequenceUtil.isBlank(userAgent) && userAgent.contains("Darwin") && method.equals(WebdavMethod.GET.getCode())) {
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
        Path prePath = Paths.get(resource.getWebappPath());
        String ossPath = CaffeineUtil.getOssPath(prePath);
        if (ossPath != null) {
            long rangeStart = getStart(range, length);
            long rangeEnd = getEnd(range, length);
            IOssService ossService = OssConfigService.getOssStorageService(ossPath);
            String objectName = WebOssService.getObjectName(prePath, ossPath, false);
            AbstractOssObject rangeObject = ossService.getAbstractOssObject(objectName, rangeStart, rangeEnd);
            super.copy(rangeObject.getInputStream(), outStream);
        } else {
            super.copy(resource, length, outStream, range);
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

        try (InputStream inStream = new BufferedInputStream(is, input)) {
            IOException copyException = copyRange(inStream, outStream);
            if (copyException != null) {
                throw copyException;
            }
        } finally {
            // 确保OSS资源被清理
            if (abstractOssObject != null) {
                closeOssObjectQuietly(abstractOssObject);
            }
        }
    }

    private void closeOssObjectQuietly(AbstractOssObject ossObject) {
        try {
            ossObject.closeObject();
        } catch (IOException e) {
            // 静默处理，不影响主流程
            Console.error("Failed to close OSS object: " + e.getMessage());
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
        deleteFile(req, resp);
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doPut(req, resp);
        createFile(req, resp);
    }

    private void deleteFile(HttpServletRequest req, HttpServletResponse resp) {
        String uri = FileNameUtils.safeDecode(req.getRequestURI());
        Path uriPath = Paths.get(uri);
        if (uriPath.getNameCount() > 1) {
            String ossPath = CaffeineUtil.getOssPath(uriPath.subpath(1, uriPath.getNameCount()));
            if (ossPath == null && resp.getStatus() == 204) {
                // 普通文件 && 删除成功
                String username = uriPath.getName(1).toString();
                String path = uriPath.subpath(1, uriPath.getNameCount()).toString();
                File file = Paths.get(fileProperties.getRootDir(), path).toFile();
                fileServiceObjectProvider.getObject().deleteFile(username, file);
            }
        }
    }

    private void createFile(HttpServletRequest req, HttpServletResponse resp) {
        String uri = FileNameUtils.safeDecode(req.getRequestURI());
        Path uriPath = Paths.get(uri);
        if (uriPath.getNameCount() > 1) {
            String ossPath = CaffeineUtil.getOssPath(uriPath.subpath(1, uriPath.getNameCount()));
            if (ossPath == null && resp.getStatus() == 201) {
                // 普通文件 && 上传成功
                String username = uriPath.getName(1).toString();
                String path = uriPath.subpath(1, uriPath.getNameCount()).toString();
                File file = Paths.get(fileProperties.getRootDir(), path).toFile();
                fileServiceObjectProvider.getObject().createFile(username, file);
            }
        }
    }

    @Override
    protected void doMove(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        super.doMove(req, resp);
        createFile(req, resp);
    }

    @Override
    protected void doMkcol(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        super.doMkcol(req, resp);
        createFile(req, resp);
    }

    public static String getPathDelimiter(String username, String folderName) {
        return MyWebdavServlet.PATH_DELIMITER + Paths.get(username, folderName);
    }

}

package com.jmal.clouddisk.webdav;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.service.impl.LogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.connector.Request;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

@Component
public class WebdavAuthenticator extends BasicAuthenticator {

    /**
     * Post、Copy、Move
     */
    private static final List<String> OPERATION_METHODS = Arrays.asList(WebdavMethod.POST.getCode(), WebdavMethod.COPY.getCode(), WebdavMethod.MOVE.getCode());

    /**
     * 不记录日志的方法 Get、PropFind、PropFind、Lock。
     */
    private static final List<String> NO_LOG_METHODS = Arrays.asList(WebdavMethod.GET.getCode(), WebdavMethod.PROPFIND.getCode(), WebdavMethod.LOCK.getCode(), WebdavMethod.UNLOCK.getCode());

    private final FileProperties fileProperties;

    private final LogService logService;

    public WebdavAuthenticator(FileProperties fileProperties, LogService logService) {
        super(fileProperties);
        this.fileProperties = fileProperties;
        this.logService = logService;
    }

    @Override
    protected boolean doAuthenticate(Request request, HttpServletResponse response) throws IOException {
        long time = System.currentTimeMillis();
        if (OPERATION_METHODS.contains(request.getMethod())) {
            setScheme(request);
        }
        boolean auth = super.doAuthenticate(request, response);
        recordLog(request, response, time);
        return auth;
    }

    private static void setScheme(Request request) {
        String destinationHeader = request.getHeader("Destination");
        if (!destinationHeader.isBlank()) {
            URI destinationUri;
            try {
                destinationUri = new URI(destinationHeader);
                request.getCoyoteRequest().scheme().setString(destinationUri.getScheme());
            } catch (URISyntaxException ignored) {

            }
        }
    }

    /**
     * 记录webDAV请求日志
     */
    private void recordLog(HttpServletRequest request, HttpServletResponse response, long time) {
        if (NO_LOG_METHODS.contains(request.getMethod())) {
            return;
        }
        LogOperation logOperation = new LogOperation();
        logOperation.setTime(System.currentTimeMillis() - time);
        String username = MyRealm.getUsernameByUri(fileProperties.getWebDavPrefixPath(), request.getRequestURI());
        logOperation.setUsername(username);
        logOperation.setOperationModule("WEBDAV");
        logOperation.setOperationFun("WebDAV请求");
        logOperation.setType(LogOperation.Type.WEBDAV.name());
        logService.addLogBefore(logOperation, null, request, response);
    }

}

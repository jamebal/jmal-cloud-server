package com.jmal.clouddisk.webdav;

import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.service.impl.LogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.authenticator.DigestAuthenticator;
import org.apache.catalina.connector.Request;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;

@Component
public class WebdavAuthenticator extends DigestAuthenticator {

    /**
     * 不记录日志的方法 Get、PropFind、PropFind、Lock。
     */
    private static final List<String> NO_LOG_METHODS = Arrays.asList(WebdavMethod.GET.getCode(), WebdavMethod.PROPFIND.getCode(), WebdavMethod.LOCK.getCode());

    private final FileProperties fileProperties;

    private final LogService logService;

    public WebdavAuthenticator(FileProperties fileProperties, LogService logService){
        super();
        this.fileProperties = fileProperties;
        this.logService = logService;
    }

    @Override
    protected boolean doAuthenticate(Request request, HttpServletResponse response) throws IOException {
        long time = System.currentTimeMillis();
        boolean auth = myAuthenticate(request, response);
        recordLog(request, response, time);
        return auth;
    }

    private boolean myAuthenticate(Request request, HttpServletResponse response) throws IOException {
        if (checkForCachedAuthentication(request, response, false)) {
            return true;
        }
        Principal principal = null;
        String authorization = request.getHeader("authorization");
        DigestInfo digestInfo = new DigestInfo(getOpaque(), getNonceValidity(),
                getKey(), nonces, isValidateUri());

        if (authorization != null && digestInfo.parse(request, authorization)) {
            principal = digestInfo.authenticate(context.getRealm());
            if (principal != null && !digestInfo.isNonceStale()) {
                String username = digestInfo.getUsername();
                String usernameUri = MyRealm.getUsernameByUri(fileProperties.getWebDavPrefixPath(), request.getRequestURI());
                if (username.equals(usernameUri)) {
                    register(request, response, principal, HttpServletRequest.DIGEST_AUTH, digestInfo.getUsername(), null);
                    return true;
                }
            }
        }
        String nonce = generateNonce(request);

        setAuthenticateHeader(request, response, nonce,
                principal != null && digestInfo.isNonceStale());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }

    /**
     * 记录webDAV请求日志
     */
    private void recordLog(HttpServletRequest request, HttpServletResponse response, long time) {
        if (NO_LOG_METHODS.contains(request.getMethod())){
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

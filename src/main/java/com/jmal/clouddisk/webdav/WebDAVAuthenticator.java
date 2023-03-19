package com.jmal.clouddisk.webdav;

import cn.hutool.core.lang.Console;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.authenticator.DigestAuthenticator;
import org.apache.catalina.connector.Request;

import java.io.IOException;
import java.security.Principal;

public class WebDAVAuthenticator extends DigestAuthenticator {

    public WebDAVAuthenticator() {
        super();
    }
    @Override
    protected boolean doAuthenticate(Request request, HttpServletResponse response) throws IOException {
        if (checkForCachedAuthentication(request, response, false)) {
            return true;
        }

        Principal principal = null;
        String authorization = request.getHeader("authorization");
        DigestInfo digestInfo = new DigestInfo(getOpaque(), getNonceValidity(),
                getKey(), nonces, isValidateUri());
        if (authorization != null) {
            if (digestInfo.parse(request, authorization)) {
                if (digestInfo.validate(request)) {
                    principal = digestInfo.authenticate(context.getRealm());
                }

                if (principal != null && !digestInfo.isNonceStale()) {
                    register(request, response, principal,
                            HttpServletRequest.DIGEST_AUTH,
                            digestInfo.getUsername(), null);
                    Console.log(request.getMethod(), "response", response.getStatus());
                    for (String headerName : response.getHeaderNames()) {
                        Console.log(headerName, response.getHeader(headerName));
                    }
                    return true;
                }
            }
        }

        // Send an "unauthorized" response and an appropriate challenge

        // Next, generate a nonce token (that is a token which is supposed
        // to be unique).
        String nonce = generateNonce(request);

        setAuthenticateHeader(request, response, nonce,
                principal != null && digestInfo.isNonceStale());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }
}

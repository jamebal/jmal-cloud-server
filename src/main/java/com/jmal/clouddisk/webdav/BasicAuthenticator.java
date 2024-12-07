package com.jmal.clouddisk.webdav;

import cn.hutool.core.codec.Base64Decoder;
import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.authenticator.AuthenticatorBase;
import org.apache.catalina.connector.Request;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Base64;

@Component
@Slf4j
@RequiredArgsConstructor
public class BasicAuthenticator extends AuthenticatorBase {

    private Charset charset = StandardCharsets.ISO_8859_1;
    private String charsetString = null;
    private boolean trimCredentials = true;

    private final FileProperties fileProperties;


    public String getCharset() {
        return charsetString;
    }


    public void setCharset(String charsetString) {
        // Only acceptable options are null, "" or "UTF-8" (case insensitive)
        if (charsetString == null || charsetString.isEmpty()) {
            charset = StandardCharsets.ISO_8859_1;
        } else if ("UTF-8".equalsIgnoreCase(charsetString)) {
            charset = StandardCharsets.UTF_8;
        } else {
            throw new IllegalArgumentException(sm.getString("basicAuthenticator.invalidCharset"));
        }
        this.charsetString = charsetString;
    }


    public boolean getTrimCredentials() {
        return trimCredentials;
    }


    public void setTrimCredentials(boolean trimCredentials) {
        this.trimCredentials = trimCredentials;
    }


    @Override
    protected boolean doAuthenticate(Request request, HttpServletResponse response) throws IOException {

        if (checkForCachedAuthentication(request, response, true)) {
            return true;
        }

        // Validate any credentials already included with this request
        MessageBytes authorization = request.getCoyoteRequest().getMimeHeaders().getValue("authorization");

        if (authorization != null) {
            authorization.toBytes();
            ByteChunk authorizationBC = authorization.getByteChunk();
            BasicCredentials credentials = null;
            try {
                credentials = new BasicCredentials(authorizationBC, charset, getTrimCredentials());
                String username = credentials.getUsername();
                String usernameByUri = MyRealm.getUsernameByUri(fileProperties.getWebDavPrefixPath(), request.getRequestURI());
                if (StrUtil.isNotBlank(usernameByUri) && usernameByUri.equals(username)) {
                    String password = credentials.getPassword();
                    Principal principal = context.getRealm().authenticate(username, password);
                    if (principal != null) {
                        register(request, response, principal, HttpServletRequest.BASIC_AUTH, username, password);
                        return true;
                    }
                }
            } catch (IllegalArgumentException iae) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("basicAuthenticator.invalidAuthorization", iae.getMessage()));
                }
            }
        } else {
            if (WebdavMethod.GET.getCode().equals(request.getMethod())) {
                String userAgent = request.getHeader("User-Agent");
                if (userAgent != null && userAgent.contains("Mozilla")) {
                    notAllowBrowser(response);
                    return false;
                }
            }
        }

        // the request could not be authenticated, so reissue the challenge
        StringBuilder value = new StringBuilder(16);
        value.append("Basic realm=\"");
        value.append(getRealmName(context));
        value.append('\"');
        if (charsetString != null && !charsetString.isEmpty()) {
            value.append(", charset=");
            value.append(charsetString);
        }
        response.setHeader(AUTH_HEADER_NAME, value.toString());
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        return false;

    }

    /**
     * 不允许浏览器访问webDAV
     */
    private void notAllowBrowser(HttpServletResponse resp) throws IOException {
        resp.setHeader("Content-type", "text/html;charset=UTF-8");
        resp.getWriter().print("<p>这是 WebDAV 接口。请使用 WebDAV 客户端访问。</p></br>");
        resp.getWriter().println("Windows : 文件资源管理器 或者 <a href='https://www.raidrive.com/'>RaiDrive</a></br>");
        resp.getWriter().println("Mac OS : Finder 或者 <a href='https://cyberduck.io/webdav/'>Cyberduck</a></br>");
        resp.getWriter().println("Android : <a href='https://play.google.com/store/apps/details?id=com.rs.explorer.filemanager'>RS 文件浏览器</a></br>");
        resp.getWriter().println("iOS : <a href='https://apps.apple.com/cn/app/documents-by-readdle/id364901807'>Documents</a></br>");
        resp.getWriter().close();
    }


    @Override
    protected String getAuthMethod() {
        return HttpServletRequest.BASIC_AUTH;
    }


    @Override
    protected boolean isPreemptiveAuthPossible(Request request) {
        MessageBytes authorizationHeader = request.getCoyoteRequest().getMimeHeaders().getValue("authorization");
        return authorizationHeader != null && authorizationHeader.startsWithIgnoreCase("basic ", 0);
    }


    /**
     * Parser for an HTTP Authorization header for BASIC authentication as per RFC 2617 section 2, and the Base64
     * encoded credentials as per RFC 2045 section 6.8.
     */
    public static class BasicCredentials {

        // the only authentication method supported by this parser
        // note: we include single white space as its delimiter
        private static final String METHOD = "basic ";

        private final Charset charset;
        private final boolean trimCredentials;
        private final ByteChunk authorization;
        private final int initialOffset;
        private int base64blobOffset;
        private int base64blobLength;

        /**
         * -- GETTER --
         *  Trivial accessor.
         *
         * @return the decoded username token as a String, which is never be <code>null</code>, but can be empty.
         */
        @Getter
        private String username = null;
        /**
         * -- GETTER --
         *  Trivial accessor.
         *
         * @return the decoded password token as a String, or <code>null</code> if no password was found in the
         *             credentials.
         */
        @Getter
        private String password = null;

        /**
         * Parse the HTTP Authorization header for BASIC authentication as per RFC 2617 section 2, and the Base64
         * encoded credentials as per RFC 2045 section 6.8.
         *
         * @param input           The header value to parse in-place
         * @param charset         The character set to use to convert the bytes to a string
         * @param trimCredentials Should leading and trailing whitespace be removed from the parsed credentials
         *
         * @throws IllegalArgumentException If the header does not conform to RFC 2617
         */
        public BasicCredentials(ByteChunk input, Charset charset, boolean trimCredentials)
                throws IllegalArgumentException {
            authorization = input;
            initialOffset = input.getStart();
            this.charset = charset;
            this.trimCredentials = trimCredentials;

            parseMethod();
            byte[] decoded = parseBase64();
            parseCredentials(decoded);
        }

        /*
         * The authorization method string is case-insensitive and must hae at least one space character as a delimiter.
         */
        private void parseMethod() throws IllegalArgumentException {
            if (authorization.startsWithIgnoreCase(METHOD, 0)) {
                // step past the auth method name
                base64blobOffset = initialOffset + METHOD.length();
                base64blobLength = authorization.getLength() - METHOD.length();
            } else {
                // is this possible, or permitted?
                throw new IllegalArgumentException(sm.getString("basicAuthenticator.notBasic"));
            }
        }

        /*
         * Decode the base64-user-pass token, which RFC 2617 states can be longer than the 76 characters per line limit
         * defined in RFC 2045. The base64 decoder will ignore embedded line break characters as well as surplus
         * surrounding white space.
         */
        private byte[] parseBase64() throws IllegalArgumentException {
            // 提取需要解码的子数组
            byte[] subArray = new byte[base64blobLength];
            System.arraycopy(authorization.getBuffer(), base64blobOffset, subArray, 0, base64blobLength);

            // 使用Java内置的Base64解码器
            byte[] decoded = Base64.getDecoder().decode(subArray);
            // restore original offset
            authorization.setStart(initialOffset);
            if (decoded == null) {
                throw new IllegalArgumentException(sm.getString("basicAuthenticator.notBase64"));
            }
            return decoded;
        }

        /*
         * Extract the mandatory username token and separate it from the optional password token. Tolerate surplus
         * surrounding white space.
         */
        private void parseCredentials(byte[] decoded) throws IllegalArgumentException {

            int colon = -1;
            for (int i = 0; i < decoded.length; i++) {
                if (decoded[i] == ':') {
                    colon = i;
                    break;
                }
            }

            if (colon < 0) {
                username = new String(decoded, charset);
                // password will remain null!
            } else {
                username = new String(decoded, 0, colon, charset);
                password = new String(decoded, colon + 1, decoded.length - colon - 1, charset);
                // tolerate surplus white space around credentials
                if (password.length() > 1 && trimCredentials) {
                    password = password.trim();
                }
            }
            // tolerate surplus white space around credentials
            if (username.length() > 1 && trimCredentials) {
                username = username.trim();
            }
        }
    }
}

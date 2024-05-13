package com.jmal.clouddisk.webdav;

import cn.hutool.core.util.StrUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.config.WebFilter;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import com.jmal.clouddisk.util.CaffeineUtil;
import com.jmal.clouddisk.util.PasswordHash;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.realm.RealmBase;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.*;

@Component
public class MyRealm extends RealmBase {

    private final UserServiceImpl userService;

    private final FileProperties fileProperties;

    /**
     * 删除资源 Options、Head、Trace、Get、PropFind、PropFind、Mkcol、Put、Post、Copy、Move、Delete。
     */
    private static final List<WebdavMethod> DELETES_METHODS = Arrays.asList(WebdavMethod.GET, WebdavMethod.HEAD, WebdavMethod.TRACE, WebdavMethod.OPTIONS, WebdavMethod.PROPFIND, WebdavMethod.PROPPATCH, WebdavMethod.MKCOL, WebdavMethod.PUT, WebdavMethod.POST, WebdavMethod.COPY, WebdavMethod.MOVE, WebdavMethod.DELETE, WebdavMethod.LOCK, WebdavMethod.UNLOCK);
    /**
     * 修改资源 Options、Head、Trace、Get、PropFind、PropFind、Mkcol、Put、Post、Copy、Move。
     */
    private static final List<WebdavMethod> UPDATE_METHODS = Arrays.asList(WebdavMethod.GET, WebdavMethod.HEAD, WebdavMethod.TRACE, WebdavMethod.OPTIONS, WebdavMethod.PROPFIND, WebdavMethod.PROPPATCH, WebdavMethod.MKCOL, WebdavMethod.PUT, WebdavMethod.POST, WebdavMethod.COPY, WebdavMethod.MOVE, WebdavMethod.LOCK, WebdavMethod.UNLOCK);
    /**
     * 创建资源 Options、Head、Trace、Get、PropFind、PropFind、Mkcol、Lock、UnLock、Put、Post。
     */
    private static final List<WebdavMethod> UPLOAD_METHODS = Arrays.asList(WebdavMethod.GET, WebdavMethod.HEAD, WebdavMethod.TRACE, WebdavMethod.OPTIONS, WebdavMethod.PROPFIND, WebdavMethod.PROPPATCH, WebdavMethod.MKCOL, WebdavMethod.PUT, WebdavMethod.POST, WebdavMethod.LOCK, WebdavMethod.UNLOCK);
    /**
     * 浏览检索资源 Options、Head、Trace、Get、PropFind、PropFind。
     */
    private static final List<WebdavMethod> LIST_METHODS = Arrays.asList(WebdavMethod.GET, WebdavMethod.HEAD, WebdavMethod.TRACE, WebdavMethod.OPTIONS, WebdavMethod.PROPFIND, WebdavMethod.PROPPATCH);

    private static final List<String> DEFAULT_ROLES = List.of("webdav");

    public MyRealm(UserServiceImpl userService, FileProperties fileProperties) {
        this.userService = userService;
        this.fileProperties = fileProperties;
    }

    @Override
    protected String getPassword(String username) {
        return null;
    }

    @Override
    protected Principal getPrincipal(String username) {
        return new GenericPrincipal(username, DEFAULT_ROLES);
    }

    @Override
    public Principal authenticate(String username, String password) {
        String hashPassword = userService.getHashPasswordUserName(username);
        if (StrUtil.isBlank(hashPassword)) {
            return null;
        }
        boolean valid = PasswordHash.validatePassword(password, hashPassword);
        return valid ? getPrincipal(username) : null;
    }

    @Override
    public boolean hasResourcePermission(Request request, Response response, SecurityConstraint[] constraints, Context context) {
        String username = MyRealm.getUsernameByUri(fileProperties.getWebDavPrefixPath(), request.getRequestURI());
        List<WebdavMethod> methods = allowMethods(username);
        WebdavMethod method = WebdavMethod.getMethod(request.getMethod());
        return methods.contains(method);
    }

    /***
     * 根据webdav uri 获取用户名
     * @param uri uri
     * @return username
     */
    public static String getUsernameByUri(String webDavPrefixPath, String uri) {
        if (uri.startsWith(WebFilter.API)) {
            uri = WebFilter.COMPILE.matcher(uri).replaceFirst("");
        }
        uri = uri.replaceFirst(webDavPrefixPath, "");
        Path path = Paths.get(uri);
        if (path.getNameCount() < 1) {
            return null;
        }
        return path.getName(0).toString();
    }

    /***
     * 根据该用户拥有的权限获取允许访问的方法列表
     * @return 允许访问的方法列表
     */
    public List<WebdavMethod> allowMethods(String username) {
        int maxAuthority = maxAuthority(username);
        return switch (maxAuthority) {
            case 3 -> DELETES_METHODS;
            case 2 -> UPDATE_METHODS;
            case 1 -> UPLOAD_METHODS;
            case 0 -> LIST_METHODS;
            default -> new ArrayList<>(0);
        };
    }

    /***
     * 用户的最大权限
     *  delete > update > upload > list
     *     3   >   2    >   1    >   0
     * @return delete/update/upload/list
     */
    public int maxAuthority(String username) {
        List<String> authorities = CaffeineUtil.getAuthoritiesCache(username);
        if (authorities == null) {
            authorities = userService.getAuthorities(username);
        }

        Map<String, Integer> authorityMap = Map.of(
                "cloud:file:delete", 3,
                "cloud:file:update", 2,
                "cloud:file:upload", 1
        );

        return authorities.stream()
                .filter(authority -> authority.startsWith("cloud:file:"))
                .map(authorityMap::get)
                .filter(Objects::nonNull)
                .max(Comparator.comparingInt(Integer::intValue))
                .orElse(0);
    }
}

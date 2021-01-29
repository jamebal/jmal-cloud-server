package com.jmal.clouddisk.webdav;

import cn.hutool.core.lang.Console;
import io.milton.http.Auth;
import io.milton.http.Request;
import io.milton.http.http11.auth.DigestGenerator;
import io.milton.http.http11.auth.DigestResponse;
import io.milton.resource.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @author jmal
 * @Description webDAV 认证授权
 * @Date 2021/1/29 2:03 下午
 */
@Component
@Slf4j
public class MySimpleSecurityManager implements io.milton.http.SecurityManager{

    private String realm;
    private Map<String,String> nameAndPasswords;
    private DigestGenerator digestGenerator;

    public MySimpleSecurityManager() {
        digestGenerator = new DigestGenerator();
    }

    public MySimpleSecurityManager( DigestGenerator digestGenerator ) {
        this.digestGenerator = digestGenerator;
    }

    public MySimpleSecurityManager( String realm, Map<String,String> nameAndPasswords ) {
        this.realm = realm;
        this.nameAndPasswords = nameAndPasswords;
        digestGenerator = new DigestGenerator();
    }

    public Object getUserByName( String name ) {
        String actualPassword = nameAndPasswords.get( name );
        if( actualPassword != null ) {
            return name;
        }
        return null;
    }

    @Override
    public Object authenticate( String user, String password ) {
        Console.error("authenticate1");
        log.debug( "authenticate: " + user + " - " + password);
        // user name will include domain when coming form ftp. we just strip it off
        if (user.contains( "@")) {
            user = user.substring( 0, user.indexOf( "@"));
        }
        String actualPassword = nameAndPasswords.get( user );
        if (actualPassword == null) {
            log.debug("user not found: " + user);
            return null;
        } else {
            return (actualPassword.equals(password)) ? user : null;
        }
    }

    @Override
    public Object authenticate( DigestResponse digestRequest ) {
        Console.error("authenticate2");
        if( digestGenerator == null ) {
            throw new RuntimeException("No digest generator is configured");
        }
        String actualPassword = nameAndPasswords.get( digestRequest.getUser() );
        String serverResponse = digestGenerator.generateDigest( digestRequest, actualPassword );
        String clientResponse = digestRequest.getResponseDigest();

        if( serverResponse.equals( clientResponse ) ) {
            return "ok";
        } else {
            return null;
        }
    }

    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth, Resource resource ) {
        if( auth == null ) {
            return false;
        } else {
            return auth.getTag() != null;
        }
    }

    @Override
    public String getRealm(String host) {
        return realm;
    }

    public void setNameAndPasswords( Map<String, String> nameAndPasswords ) {
        this.nameAndPasswords = nameAndPasswords;
    }

    public void setDigestGenerator(DigestGenerator digestGenerator) {
        this.digestGenerator = digestGenerator;
    }

    @Override
    public boolean isDigestAllowed() {
        return digestGenerator != null;
    }

    public DigestGenerator getDigestGenerator() {
        return digestGenerator;
    }
}

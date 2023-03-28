package com.jmal.clouddisk.webdav.resource;

import com.jmal.clouddisk.oss.FileInfo;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;

import java.io.InputStream;
import java.net.URL;
import java.security.cert.Certificate;
import java.util.jar.Manifest;

public class EmptyOSSResource implements WebResource {

    private final WebResourceRoot root;
    private final String webAppPath;
    private final FileInfo resource;

    public EmptyOSSResource(WebResourceRoot root, String webAppPath) {
        this(root, webAppPath, null);
    }

    public EmptyOSSResource(WebResourceRoot root, String webAppPath, FileInfo resource) {
        this.root = root;
        this.webAppPath = webAppPath;
        this.resource = resource;
    }

    @Override
    public long getLastModified() {
        return 0;
    }

    @Override
    public String getLastModifiedHttp() {
        return null;
    }

    @Override
    public boolean exists() {
        return false;
    }

    @Override
    public boolean isVirtual() {
        return false;
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public boolean isFile() {
        return false;
    }

    @Override
    public boolean delete() {
        return false;
    }

    @Override
    public String getName() {
        int index = webAppPath.lastIndexOf('/');
        if (index == -1) {
            return webAppPath;
        } else {
            return webAppPath.substring(index + 1);
        }
    }

    @Override
    public long getContentLength() {
        return -1;
    }

    @Override
    public String getCanonicalPath() {
        return "";
    }

    @Override
    public boolean canRead() {
        return false;
    }

    @Override
    public String getWebappPath() {
        return webAppPath;
    }

    @Override
    public String getETag() {
        return null;
    }

    @Override
    public void setMimeType(String mimeType) {
        // NOOP
    }

    @Override
    public String getMimeType() {
        return null;
    }

    @Override
    public InputStream getInputStream() {
        return null;
    }

    @Override
    public byte[] getContent() {
        return null;
    }

    @Override
    public long getCreation() {
        return 0;
    }

    @Override
    public URL getURL() {
        return null;
    }

    @Override
    public URL getCodeBase() {
        return null;
    }

    @Override
    public Certificate[] getCertificates() {
        return null;
    }

    @Override
    public Manifest getManifest() {
        return null;
    }

    @Override
    public WebResourceRoot getWebResourceRoot() {
        return root;
    }
}

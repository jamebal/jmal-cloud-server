package com.jmal.clouddisk.webdav;

import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.webresources.DirResourceSet;

public class MyDirResourceSet extends DirResourceSet {

    public MyDirResourceSet(WebResourceRoot root, String base) {
        super(root, "/", base, "/");
    }

    @Override
    public WebResource getResource(String path) {
        checkPath(path);
        return super.getResource(path);
    }

    @Override
    public String[] list(String path) {
        checkPath(path);
        return super.list(path);
    }
}

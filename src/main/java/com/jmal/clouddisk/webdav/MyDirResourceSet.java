package com.jmal.clouddisk.webdav;

import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.webresources.DirResourceSet;

public class MyDirResourceSet extends DirResourceSet {

    public MyDirResourceSet(WebResourceRoot root, String webAppMount, String base,
                            String internalPath) {
        super(root, webAppMount, base, internalPath);
    }

    @Override
    public boolean mkdir(String path) {
        return super.mkdir(path);
    }
}

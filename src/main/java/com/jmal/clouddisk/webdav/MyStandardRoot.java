package com.jmal.clouddisk.webdav;

import org.apache.catalina.Context;
import org.apache.catalina.WebResourceSet;
import org.apache.catalina.webresources.StandardRoot;

public class MyStandardRoot extends StandardRoot {
    public MyStandardRoot(Context context) {
        super(context);
    }

    @Override
    protected WebResourceSet createMainResourceSet() {
        MyDirResourceSet myDirResourceSet = null;
        for (WebResourceSet preResource : getPreResources()) {
            if (preResource instanceof MyDirResourceSet myDirResourceSet1) {
                myDirResourceSet = myDirResourceSet1;
            }
        }
        if (myDirResourceSet != null) {
            return myDirResourceSet;
        }
        return super.createMainResourceSet();
    }

    @Override
    public boolean mkdir(String path) {
        return super.mkdir(path);
    }
}

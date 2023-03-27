package com.jmal.clouddisk.webdav;

import com.jmal.clouddisk.webdav.resource.FileResourceSet;
import org.apache.catalina.Context;
import org.apache.catalina.WebResourceSet;
import org.apache.catalina.webresources.StandardRoot;

public class MyStandardRoot extends StandardRoot {
    public MyStandardRoot(Context context) {
        super(context);
    }

    @Override
    protected WebResourceSet createMainResourceSet() {
        FileResourceSet myDirResourceSet = null;
        for (WebResourceSet preResource : getPreResources()) {
            if (preResource instanceof FileResourceSet myDirResourceSet1) {
                myDirResourceSet = myDirResourceSet1;
            }
        }
        if (myDirResourceSet != null) {
            return myDirResourceSet;
        }
        return super.createMainResourceSet();
    }

}

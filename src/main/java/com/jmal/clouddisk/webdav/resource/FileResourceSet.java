package com.jmal.clouddisk.webdav.resource;

import com.jmal.clouddisk.oss.FileInfo;
import com.jmal.clouddisk.oss.OssConfigService;
import com.jmal.clouddisk.util.CaffeineUtil;
import org.apache.catalina.WebResource;
import org.apache.catalina.WebResourceRoot;
import org.apache.catalina.util.ResourceSet;
import org.apache.catalina.webresources.AbstractFileResourceSet;
import org.apache.catalina.webresources.EmptyResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Set;

/**
 * @author jmal
 * @Description FileResourceSet
 * @date 2023/3/27 11:47
 */
public class FileResourceSet extends AbstractFileResourceSet {


    public FileResourceSet(WebResourceRoot root, String base) {
        super("/");
        setRoot(root);
        setWebAppMount("/");
        setBase(base);
    }


    @Override
    public WebResource getResource(String path) {
        checkPath(path);
        WebResourceRoot root = getRoot();
        File f;
        Path prePath = Paths.get(path);
        String ossPath = CaffeineUtil.getOssPath(prePath);
        if (ossPath != null && prePath.getNameCount() > 2) {
            path = path.substring(ossPath.length() + 1);
            FileInfo fileInfo = OssConfigService.getOssStorageService(ossPath).getFileInfo(path);
            if (fileInfo == null) {
                return new EmptyResource(root, path);
            }
            return new OssFileResource(root, path, fileInfo, isReadOnly(), getManifest(), OssConfigService.getOssStorageService(ossPath));
        } else {
            f = file(path, false);
        }
        if (f == null) {
            return new EmptyResource(root, path);
        }
        if (!f.exists()) {
            return new EmptyResource(root, path, f);
        }
        if (f.isDirectory() && path.charAt(path.length() - 1) != '/') {
            path = path + '/';
        }
        return new LocalFileResource(root, path, f, isReadOnly(), getManifest());
    }

    @Override
    public String[] list(String path) {
        checkPath(path);

        File f;
        String ossPath = CaffeineUtil.getOssPath(Paths.get(path));
        if (ossPath != null) {
            String name = OssConfigService.getObjectName(path, ossPath);
            return OssConfigService.getOssStorageService(ossPath).list(name);
        } else {
            f = file(path, true);
        }

        if (f == null) {
            return EMPTY_STRING_ARRAY;
        }
        String[] result = f.list();
        return Objects.requireNonNullElse(result, EMPTY_STRING_ARRAY);
    }

    @Override
    public Set<String> listWebAppPaths(String path) {
        return new ResourceSet<>();
    }

    @Override
    public boolean mkdir(String path) {
        checkPath(path);
        if (isReadOnly()) {
            return false;
        }

        File f;
        String ossPath = CaffeineUtil.getOssPath(Paths.get(path));
        if (ossPath != null) {
            String name = OssConfigService.getObjectName(path, ossPath);
            return OssConfigService.getOssStorageService(ossPath).mkdir(name);
        } else {
            f = file(path, false);
        }

        if (f == null) {
            return false;
        }
        return f.mkdir();
    }

    @Override
    public boolean write(String path, InputStream is, boolean overwrite) {
        checkPath(path);

        if (is == null) {
            throw new NullPointerException(
                    sm.getString("dirResourceSet.writeNpe"));
        }

        if (isReadOnly()) {
            return false;
        }

        if (path.endsWith("/")) {
            return false;
        }

        File dest;
        Path prePath = Paths.get(path);
        String ossPath = CaffeineUtil.getOssPath(prePath);
        if (ossPath != null) {
            if (prePath.getNameCount() > 2) {
                path = path.substring(ossPath.length() + 1);
            }
            return OssConfigService.getOssStorageService(ossPath).write(is, ossPath, path);
        } else {
            dest = file(path, false);
        }

        if (dest == null) {
            return false;
        }

        if (dest.exists() && !overwrite) {
            return false;
        }

        try {
            if (overwrite) {
                Files.copy(is, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(is, dest.toPath());
            }
        } catch (IOException ioe) {
            return false;
        }

        return true;
    }

    @Override
    protected void checkType(File file) {
        if (!file.isDirectory()) {
            throw new IllegalArgumentException(sm.getString("dirResourceSet.notDirectory",
                    getBase(), File.separator, getInternalPath()));
        }
    }

}

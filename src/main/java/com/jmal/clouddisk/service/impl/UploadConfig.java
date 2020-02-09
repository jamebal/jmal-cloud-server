package com.jmal.clouddisk.service.impl;

import java.io.File;

/**
 * @Description UploadConfig
 * @Date 2020-01-14 11:30
 * @blame jmal
 */
public class UploadConfig {

    /***
     * 后台统一存放文件的路径
     */
    public static final String SERVER_PATH = System.getProperty("user.dir") + File.separator + "uploadFile";
}

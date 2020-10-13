package com.jmal.clouddisk.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @Description UploadResponse
 * @Author jmal
 * @Date 2020-01-27 17:04
 * @author jmal
 */
@Data
public class UploadResponse {

    /***
     * 服务是否已经存在该文件,通过文件的md5校验
     */
    boolean pass;
    /***
     * 代表这些分片是已经上传过的了
     */
    List<Integer> resume;
    boolean upload;
    boolean merge;

    public UploadResponse() {
        this.pass = false;
        this.upload = false;
        this.merge = false;
        this.resume = new ArrayList<>();
    }
}

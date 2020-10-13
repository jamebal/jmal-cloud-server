package com.jmal.clouddisk.model;
import lombok.Data;

/**
 * @Description 合并上传文件接口参数
 * @Author jmal
 * @Date 2020-01-14 11:38
 * @author jmal
 */
@Data
public class UploadActionApiParam {
    /***
     * 根据前台的action参数决定要做的动作
     */
    String action;
    /***
     * 文件唯一表示
     */
    String fileMd5;
    String fileName;
    /***
     * 当前分块下标
     */
    String chunk;
    /***
     * 当前分块大小
     */
    String chunkSize;
}

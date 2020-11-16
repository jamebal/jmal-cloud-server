package com.jmal.clouddisk.model;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author jmal
 * @Description 图片上传传输对象
 * @Date 2020/11/16 11:12 上午
 */
@Data
public class UploadImageDTO {
    String filename;
    String username;
    String userId;
    MultipartFile[] files;
}

package com.jmal.clouddisk.model;

import com.jmal.clouddisk.config.Reflective;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author jmal
 * @Description 图片上传传输对象
 * @Date 2020/11/16 11:12 上午
 */
@Data
public class UploadImageDTO implements Reflective {
    String username;
    String userId;
    /**
     * markdown文件id
     */
    String fileId;
    /**
     * 远程url图像
     */
    String url;
    MultipartFile[] files;
}

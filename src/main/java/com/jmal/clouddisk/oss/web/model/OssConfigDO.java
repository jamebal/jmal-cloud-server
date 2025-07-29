package com.jmal.clouddisk.oss.web.model;

import com.jmal.clouddisk.oss.OssConfigService;
import com.jmal.clouddisk.oss.PlatformOSS;
import lombok.Data;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * @author jmal
 * @Description oss 配置
 * @date 2023/4/4 16:08
 */
@Data
@Document(collection = OssConfigService.COLLECTION_NAME)
@CompoundIndex(name = "userId_1", def = "{'userId': 1}")
public class OssConfigDO {
    private String id;
    private PlatformOSS platform;
    private String folderName;
    private String endpoint;
    private String accessKey;
    private String secretKey;
    private String region;
    private String bucket;
    private String userId;

    public OssConfigDTO toOssConfigDTO(TextEncryptor textEncryptor) {
        OssConfigDTO ossConfigDTO = new OssConfigDTO();
        ossConfigDTO.setEndpoint(this.endpoint);
        ossConfigDTO.setBucket(this.bucket);
        ossConfigDTO.setFolderName(this.folderName);
        ossConfigDTO.setPlatform(this.platform.name());
        ossConfigDTO.setRegion(this.region);
        ossConfigDTO.setUserId(this.userId);
        ossConfigDTO.setAccessKey(textEncryptor.decrypt(accessKey));
        ossConfigDTO.setSecretKey(textEncryptor.decrypt(secretKey));
        return ossConfigDTO;
    }

    public OssConfigDTO toOssConfigDTO() {
        OssConfigDTO ossConfigDTO = new OssConfigDTO();
        ossConfigDTO.setId(this.id);
        ossConfigDTO.setEndpoint(this.endpoint);
        ossConfigDTO.setBucket(this.bucket);
        ossConfigDTO.setFolderName(this.folderName);
        ossConfigDTO.setPlatform(this.platform.getKey());
        ossConfigDTO.setRegion(this.region);
        ossConfigDTO.setUserId(this.userId);
        return ossConfigDTO;
    }
}

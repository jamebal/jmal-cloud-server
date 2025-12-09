package com.jmal.clouddisk.oss.web.model;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.oss.PlatformOSS;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * @author jmal
 * @Description oss 配置
 * @date 2023/4/4 16:14
 */
@Data
@Schema
public class OssConfigDTO implements Reflective {

    @NotNull(message = "platform 不能为空")
    @Schema(name = "platform", title = "platform", requiredMode = Schema.RequiredMode.REQUIRED, example = "aliyun")
    private String platform;
    @NotNull(message = "folderName 不能为空")
    @Schema(name = "folderName", title = "folderName", requiredMode = Schema.RequiredMode.REQUIRED, example = "aliyunoss")
    private String folderName;
    @NotNull(message = "endpoint 不能为空")
    @Schema(name = "endpoint", title = "endpoint", requiredMode = Schema.RequiredMode.REQUIRED)
    private String endpoint;
    @NotNull(message = "accessKey 不能为空")
    @Schema(name = "accessKey", title = "accessKey", requiredMode = Schema.RequiredMode.REQUIRED, example = " ")
    private String accessKey;
    @NotNull(message = "secretKey 不能为空")
    @Schema(name = "secretKey", title = "secretKey", requiredMode = Schema.RequiredMode.REQUIRED, example = " ")
    private String secretKey;
    @Schema(name = "region", title = "region", example = "region")
    private String region;
    @NotNull(message = "bucket 不能为空")
    @Schema(name = "bucket", title = "bucket", requiredMode = Schema.RequiredMode.REQUIRED, example = "bucket name")
    private String bucket;
    @NotNull(message = "userId 不能为空")
    @Schema(name = "userId", title = "userId", requiredMode = Schema.RequiredMode.REQUIRED, example = "60388f6ca45a48cbb3248f7e")
    private String userId;
    @Schema(hidden = true)
    private String username;
    @Schema(hidden = true)
    private String id;

    @Schema(name = "pathStyleAccessEnabled", title = "pathStyleAccessEnabled", description = "启用路径风格访问以访问S3对象，而非DNS风格访问")
    private Boolean pathStyleAccessEnabled;

    @Schema(name = "proxyEnabled", title = "proxyEnabled", description = "是否启用 S3 代理功能, 启用后上传下载流量会通过jmalcloud服务中转, 默认关闭")
    private Boolean proxyEnabled;

    public OssConfigDO toOssConfigDO(OssConfigDO ossConfigDO, TextEncryptor textEncryptor) {
        ossConfigDO.setEndpoint(this.endpoint);
        ossConfigDO.setPlatform(PlatformOSS.getPlatform(this.platform));
        ossConfigDO.setFolderName(this.folderName);
        ossConfigDO.setBucket(this.bucket);
        ossConfigDO.setRegion(this.region);
        ossConfigDO.setUserId(this.userId);
        ossConfigDO.setProxyEnabled(this.proxyEnabled);
        ossConfigDO.setPathStyleAccessEnabled(this.pathStyleAccessEnabled);
        ossConfigDO.setAccessKey(textEncryptor.encrypt(accessKey));
        ossConfigDO.setSecretKey(textEncryptor.encrypt(secretKey));
        return ossConfigDO;
    }

    public OssConfigDO toOssConfigDO(TextEncryptor textEncryptor) {
        return toOssConfigDO(new OssConfigDO(), textEncryptor);
    }
}

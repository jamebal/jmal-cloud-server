package com.jmal.clouddisk.oss;

import cn.hutool.core.lang.Console;
import com.aliyun.sts20150401.Client;
import com.aliyun.sts20150401.models.AssumeRoleRequest;
import com.aliyun.sts20150401.models.AssumeRoleResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AliyunService {

    /**
     * 获取appToken
     */
    public ResponseResult<Object> getAppToken() throws Exception {
        // 工程代码泄露可能会导致AccessKey泄露，并威胁账号下所有资源的安全性。以下代码示例仅供参考，建议使用更安全的 STS 方式，更多鉴权访问方式请参见：https://help.aliyun.com/document_detail/378657.html
        Client client = createClient();
        AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest();
        RuntimeOptions runtime = new RuntimeOptions();
        try {
            assumeRoleRequest.setRoleArn("acs:ram::1881235421717477:role/ossrole");
            assumeRoleRequest.setRoleSessionName("jmalcloud-001");
            // 复制代码运行请自行打印 API 的返回值
            AssumeRoleResponse response = client.assumeRoleWithOptions(assumeRoleRequest, runtime);
            Map<String, String> respMap = new LinkedHashMap<>();
            respMap.put("AccessKeyId", response.getBody().getCredentials().getAccessKeyId());
            respMap.put("AccessKeySecret", response.getBody().getCredentials().getAccessKeySecret());
            respMap.put("SecurityToken", response.getBody().getCredentials().getSecurityToken());
            respMap.put("Expiration", response.getBody().getCredentials().getExpiration());
            return ResultUtil.success(respMap);
        } catch (TeaException error) {
            // 如有需要，请打印 error
            Console.error(error.getMessage(), error);
        } catch (Exception exception) {
            Console.error(exception.getMessage(), exception);
        }
       return ResultUtil.error("sts服务认证失败");
    }

    /**
     * 使用AK&SK初始化账号Client
     * @return Client
     */
    public static Client createClient() throws Exception {
        Config config = new Config()
                // 必填，您的 AccessKey ID
                .setAccessKeyId("")
                // 必填，您的 AccessKey Secret
                .setAccessKeySecret("");
        // 访问的域名
        config.endpoint = "sts.cn-guangzhou.aliyuncs.com";
        return new Client(config);
    }

}

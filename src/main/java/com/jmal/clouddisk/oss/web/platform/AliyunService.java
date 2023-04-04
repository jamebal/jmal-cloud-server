package com.jmal.clouddisk.oss.web.platform;

import cn.hutool.core.lang.Console;
import com.aliyun.sts20150401.Client;
import com.aliyun.sts20150401.models.AssumeRoleRequest;
import com.aliyun.sts20150401.models.AssumeRoleResponse;
import com.aliyun.tea.TeaException;
import com.aliyun.teaopenapi.models.Config;
import com.aliyun.teautil.models.RuntimeOptions;
import com.jmal.clouddisk.oss.web.IOssWebService;
import com.jmal.clouddisk.oss.web.STSObjectVO;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import org.springframework.stereotype.Service;

@Service
public class AliyunService implements IOssWebService {

    @Override
    public ResponseResult<STSObjectVO> getAppToken() {
        try {
            Client client = createClient();
            AssumeRoleRequest assumeRoleRequest = new AssumeRoleRequest();
            RuntimeOptions runtime = new RuntimeOptions();
            STSObjectVO stsObjectVO = new STSObjectVO();
            assumeRoleRequest.setRoleArn("acs:ram::1881235421717477:role/ossrole");
            assumeRoleRequest.setRoleSessionName("jmalcloud-001");
            // 复制代码运行请自行打印 API 的返回值
            AssumeRoleResponse response = client.assumeRoleWithOptions(assumeRoleRequest, runtime);
            stsObjectVO.setAccessKeyId(response.getBody().getCredentials().getAccessKeyId());
            stsObjectVO.setAccessKeySecret(response.getBody().getCredentials().getAccessKeySecret());
            stsObjectVO.setSecurityToken(response.getBody().getCredentials().getSecurityToken());
            stsObjectVO.setExpiration(response.getBody().getCredentials().getExpiration());
            return ResultUtil.success(stsObjectVO);
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

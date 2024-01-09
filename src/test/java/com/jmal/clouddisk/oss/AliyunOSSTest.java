package com.jmal.clouddisk.oss;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.ListObjectsRequest;
import com.aliyun.oss.model.OSSObjectSummary;
import com.aliyun.oss.model.ObjectListing;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * @author jmal
 * @Description AliyunOSSTest
 * @date 2023/3/27 17:50
 */
@SpringBootTest
class AliyunOSSTest {

    private static String endpoint = "https://oss-cn-guangzhou.aliyuncs.com";
    private static final String accessKeyId = "";
    private static final String accessKeySecret = "";
    private static final String bucketName = "jmalcloud";

    @Test
    void listFile(){

        // 创建OSSClient实例。
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            // 构造ListObjectsRequest请求。
            ListObjectsRequest listObjectsRequest = new ListObjectsRequest(bucketName);

            // 设置正斜线（/）为文件夹的分隔符。
            listObjectsRequest.setDelimiter("/");

            // 列出fun目录下的所有文件和文件夹。
            listObjectsRequest.setPrefix("jmalcloud/abc/");

            ObjectListing listing = ossClient.listObjects(listObjectsRequest);

            // 遍历所有文件。
            System.out.println("Objects:");
            // objectSummaries的列表中给出的是fun目录下的文件。
            for (OSSObjectSummary objectSummary : listing.getObjectSummaries()) {
                System.out.println(objectSummary.getKey());
            }

            // 遍历所有commonPrefix。
            System.out.println("\nCommonPrefixes:");
            // commonPrefixs列表中显示的是fun目录下的所有子文件夹。由于fun/movie/001.avi和fun/movie/007.avi属于fun文件夹下的movie目录，因此这两个文件未在列表中。
            for (String commonPrefix : listing.getCommonPrefixes()) {
                System.out.println(commonPrefix);
            }
        } catch (OSSException oe) {
            System.out.println("""
                    Caught an OSSException, which means your request made it to OSS, \
                    but was rejected with an error response for some reason.\
                    """);
            System.out.println("Error Message:" + oe.getErrorMessage());
            System.out.println("Error Code:" + oe.getErrorCode());
            System.out.println("Request ID:" + oe.getRequestId());
            System.out.println("Host ID:" + oe.getHostId());
        } catch (ClientException ce) {
            System.out.println("""
                    Caught an ClientException, which means the client encountered \
                    a serious internal problem while trying to communicate with OSS, \
                    such as not being able to access the network.\
                    """);
            System.out.println("Error Message:" + ce.getMessage());
        } finally {
            if (ossClient != null) {
                ossClient.shutdown();
            }
        }
    }

}

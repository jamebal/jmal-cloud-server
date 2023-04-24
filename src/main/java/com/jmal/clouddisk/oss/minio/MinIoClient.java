package com.jmal.clouddisk.oss.minio;

import com.google.common.collect.Multimap;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.Part;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * @author jmal
 * @Description MinIoClient
 * @date 2023/4/24 14:24
 */
@Slf4j
public class MinIoClient extends MinioAsyncClient {
    protected MinIoClient(MinioAsyncClient client) {
        super(client);
    }

    public String initMultiPartUpload(String bucket, String region, String object, Multimap<String, String> headers) throws InsufficientDataException, NoSuchAlgorithmException, IOException, InvalidKeyException, XmlParserException, InternalException, ServerException, ErrorResponseException, InvalidResponseException {
        try {
            CompletableFuture<CreateMultipartUploadResponse> response = this.createMultipartUploadAsync(bucket, region, object, headers, null);
            return response.get().result().uploadId();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throwEncapsulatedException(e);
        }
        return null;
    }

    public ObjectWriteResponse mergeMultipartUpload(String bucketName, String region, String objectName, String uploadId, Part[] parts) throws InsufficientDataException, IOException, NoSuchAlgorithmException, InvalidKeyException, XmlParserException, InternalException, ServerException, ErrorResponseException, InvalidResponseException {
        try {
            return this.completeMultipartUploadAsync(
                    bucketName,
                    region,
                    objectName,
                    uploadId,
                    parts,
                    null,
                    null).get();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throwEncapsulatedException(e);
        }
        return null;
    }

    public ListPartsResponse listMultipart(String bucketName, String region, String objectName, Integer maxParts, Integer partNumberMarker, String uploadId) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        try {
            CompletableFuture<ListPartsResponse> listPartsResponse = this.listPartsAsync(bucketName, region, objectName, maxParts, partNumberMarker, uploadId, null, null);
            return listPartsResponse.get();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throwEncapsulatedException(e);
        }
        return null;
    }

    public void abortMultipartUpload(String bucketName, String region, String objectName, String uploadId) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        try {
            CompletableFuture<AbortMultipartUploadResponse> abortMultipartUploadAsync = this.abortMultipartUploadAsync(bucketName, region, objectName, uploadId, null, null);
            abortMultipartUploadAsync.get();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throwEncapsulatedException(e);
        }
    }

    public UploadPartResponse uploadPart(String bucketName, String region, String objectName, Object data, long length, String uploadId, int partNumber) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        try {
            CompletableFuture<UploadPartResponse> uploadPartResponse = this.uploadPartAsync(bucketName, region, objectName, data, length, uploadId, partNumber, null, null);
            return uploadPartResponse.get();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throwEncapsulatedException(e);
        }
        return null;
    }


}

package com.jmal.clouddisk.oss.minio;

import com.google.common.collect.Multimap;
import io.minio.*;
import io.minio.errors.*;
import io.minio.messages.Part;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
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

    public boolean bucketExists(String bucketName)
            throws ErrorResponseException, InsufficientDataException, InternalException,
            InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException,
            ServerException, XmlParserException {
        try {
            BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder().bucket(bucketName).build();
            return super.bucketExists(bucketExistsArgs).get();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throwEncapsulatedException(e);
        }
        return false;
    }

    public StatObjectResponse statObject(String bucketName, String objectName)
            throws ErrorResponseException, InsufficientDataException, InternalException,
            InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException,
            ServerException, XmlParserException {
        try {
            StatObjectArgs args = StatObjectArgs.builder().bucket(bucketName).object(objectName).build();
            return super.statObject(args).get();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throwEncapsulatedException(e);
        }
        return null;
    }

    public GetObjectResponse getObject(String bucketName, String objectName, Long rangeStart, Long rangeEnd)
            throws ErrorResponseException, InsufficientDataException, InternalException,
            InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException,
            ServerException, XmlParserException {
        try {
            GetObjectArgs objectArgs;
            if (rangeStart != null && rangeEnd != null) {
                long contentLength = rangeEnd - rangeStart + 1;
                objectArgs = GetObjectArgs
                        .builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .offset(rangeStart)
                        .length(contentLength)
                        .build();
            } else {
                objectArgs = GetObjectArgs
                        .builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .build();
            }
            return super.getObject(objectArgs).get();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throwEncapsulatedException(e);
        }
        return null;
    }

    public void downloadObject(String bucketName, String objectName, File file)
            throws ErrorResponseException, InsufficientDataException, InternalException,
            InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException,
            ServerException, XmlParserException {
        try {
            DownloadObjectArgs downloadObjectArgs = DownloadObjectArgs.builder().bucket(bucketName).object(objectName).filename(file.getAbsolutePath()).build();
            super.downloadObject(downloadObjectArgs).get();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throwEncapsulatedException(e);
        }
    }

    public void removeObject(String bucketName, String objectName)
            throws ErrorResponseException, InsufficientDataException, InternalException,
            InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException,
            ServerException, XmlParserException {
        try {
            RemoveObjectArgs removeObjectArgs = RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build();
            super.removeObject(removeObjectArgs).get();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throwEncapsulatedException(e);
        }
    }

    public ObjectWriteResponse putObject2(PutObjectArgs args)
            throws ErrorResponseException, InsufficientDataException, InternalException,
            InvalidKeyException, InvalidResponseException, IOException, NoSuchAlgorithmException,
            ServerException, XmlParserException {
        try {
            return super.putObject(args).get();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throwEncapsulatedException(e);
        }
        return null;
    }

    public String initMultiPartUpload(String bucket, String region, String object, Multimap<String, String> headers) throws InsufficientDataException, NoSuchAlgorithmException, IOException, InvalidKeyException, XmlParserException, InternalException, ServerException, ErrorResponseException, InvalidResponseException {
        try {
            CompletableFuture<CreateMultipartUploadResponse> response = super.createMultipartUploadAsync(bucket, region, object, headers, null);
            return response.get().result().uploadId();
        } catch (InterruptedException e) {
            log.error(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            throwEncapsulatedException(e);
        }
        return null;
    }

    public void mergeMultipartUpload(String bucketName, String region, String objectName, String uploadId, Part[] parts) throws InsufficientDataException, IOException, NoSuchAlgorithmException, InvalidKeyException, XmlParserException, InternalException, ServerException, ErrorResponseException, InvalidResponseException {
        try {
            this.completeMultipartUploadAsync(
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
    }

    public ListPartsResponse listMultipart(String bucketName, String region, String objectName, Integer maxParts, Integer partNumberMarker, String uploadId) throws ServerException, InsufficientDataException, ErrorResponseException, IOException, NoSuchAlgorithmException, InvalidKeyException, InvalidResponseException, XmlParserException, InternalException {
        try {
            CompletableFuture<ListPartsResponse> listPartsResponse = listPartsAsync(bucketName, region, objectName, maxParts, partNumberMarker, uploadId, null, null);
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
            CompletableFuture<AbortMultipartUploadResponse> abortMultipartUploadAsync = abortMultipartUploadAsync(bucketName, region, objectName, uploadId, null, null);
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
            CompletableFuture<UploadPartResponse> uploadPartResponse = uploadPartAsync(bucketName, region, objectName, data, length, uploadId, partNumber, null, null);
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

package com.jmal.clouddisk.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.FileInputStream;

public interface FileService {

    String upload(MultipartFile multipartFile);

    String uploads(MultipartFile[] multipartFiles);

    FileInputStream getFileBlob(String imgPath);
}

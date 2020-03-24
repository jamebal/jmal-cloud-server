package com.jmal.clouddisk.service.impl;

import com.jmal.clouddisk.model.FilePropertie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * @Description 配合FilePropertie使用
 * @Author jmal
 * @Date 2020-03-24 14:03
 */
@Service
public class FileService {
    @Autowired
    FilePropertie filePropertie;
}

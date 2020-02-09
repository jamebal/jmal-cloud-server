package com.jmal.clouddisk.service;


import com.jmal.clouddisk.model.UploadActionApiParam;

import java.io.IOException;
import java.util.Map;

/**
 * IUploadActionServer
 *
 * @blame jmal
 */
public interface IUploadActionService {
    Map<String,Object> mergeFile(UploadActionApiParam param) throws IOException;
}

package com.jmal.clouddisk.oss;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author jmal
 * @Description 虚拟文件服务
 * @date 2023/3/30 11:59
 */
@Service
public class VirtualFileService {

    private final Map<String, Map<String, FileInfo>> virtualFileMap = new ConcurrentHashMap<>();
}

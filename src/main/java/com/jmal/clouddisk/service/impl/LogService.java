package com.jmal.clouddisk.service.impl;

import com.jmal.clouddisk.annotation.LogOperation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * @author jmal
 * @Description 日志服务
 * @Date 2021/2/5 5:43 下午
 */
@Service
public class LogService {

    @Autowired
    private MongoTemplate mongoTemplate;

    public void addLog(LogOperation logOperation){
        logOperation.setCreateTime(LocalDateTime.now());
        mongoTemplate.save(logOperation);
    }
}

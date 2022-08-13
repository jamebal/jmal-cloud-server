package com.jmal.clouddisk.office.callbacks.implementations;

import cn.hutool.http.HttpUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.office.callbacks.Callback;
import com.jmal.clouddisk.office.callbacks.Status;
import com.jmal.clouddisk.office.model.Track;
import com.jmal.clouddisk.service.impl.FileServiceImpl;
import com.jmal.clouddisk.service.impl.UserLoginHolder;
import com.jmal.clouddisk.util.TimeUntils;
import com.mongodb.client.result.UpdateResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;

/**
 * @author jmal
 * @Description 在执行保存请求时处理回调
 * @date 2022/8/11 16:29
 */
@Component
@Slf4j
public class SaveCallback implements Callback {

    final
    FileServiceImpl fileService;

    final
    FileProperties fileProperties;

    final
    UserLoginHolder userLoginHolder;

    final
    MongoTemplate mongoTemplate;

    public SaveCallback(FileServiceImpl fileService, FileProperties fileProperties, UserLoginHolder userLoginHolder, MongoTemplate mongoTemplate) {
        this.fileService = fileService;
        this.fileProperties = fileProperties;
        this.userLoginHolder = userLoginHolder;
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public int handle(Track body) {
        int result = 0;
        try {
            FileDocument fileDocument = mongoTemplate.findById(body.getFileId(), FileDocument.class, FileServiceImpl.COLLECTION_NAME);
            if (fileDocument == null) {
                return 1;
            }
            Path path = Paths.get(fileProperties.getRootDir(), userLoginHolder.getUsername(), fileDocument.getPath(), fileDocument.getName());
            // 下载最新的文件
            long size = HttpUtil.downloadFile(body.getUrl(), path.toString());
            String md5 = size + "/" + fileDocument.getName();
            LocalDateTime updateDate = LocalDateTime.now(TimeUntils.ZONE_ID);
            // 修改数据库
            Query query = new Query().addCriteria(Criteria.where("_id").is(body.getFileId()));
            Update update = new Update();
            update.set("size", size);
            update.set("md5", md5);
            update.set("updateDate", updateDate);
            UpdateResult updateResult = mongoTemplate.updateFirst(query, update, FileServiceImpl.COLLECTION_NAME);
            if (updateResult.getModifiedCount() != 1) {
                result =  1;
            }
            // 推送修改文件的通知
            fileDocument.setSize(size);
            fileDocument.setUpdateDate(updateDate);
            fileDocument.setMd5(md5);
            fileService.pushMessage(userLoginHolder.getUsername(), fileDocument, "updateFile");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return result;
    }

    @Override
    public int getStatus() {
        // 2 - 文件已准备好保存
        return Status.SAVE.getCode();
    }
}

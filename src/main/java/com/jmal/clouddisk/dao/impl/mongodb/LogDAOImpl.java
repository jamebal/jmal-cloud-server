package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.ILogDAO;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.LogOperationDTO;
import com.jmal.clouddisk.model.file.FileDocument;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.util.TimeUntils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class LogDAOImpl implements ILogDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public void save(LogOperation logOperation) {
        mongoTemplate.save(logOperation);
    }

    @Override
    public Page<LogOperation> findAllByQuery(LogOperationDTO logOperationDTO, String currentUsername, String currentUserId, boolean isAdministrators) {
        Query query = getQuery(logOperationDTO, currentUsername, currentUserId, isAdministrators);
        return getLogOperations(logOperationDTO, query);
    }

    @Override
    public long countByUrl(String url) {
        Query query = new Query();
        query.addCriteria(Criteria.where("url").is(url));
        return mongoTemplate.count(query, LogOperation.class);
    }

    @Override
    public Page<LogOperation> findFileOperationHistoryByFileId(LogOperationDTO logOperationDTO, String fileId, String currentUserId, String currentUsername) {
        Query query = getFileOperationHistoryQuery(fileId, currentUserId, currentUsername);
        if (query == null) {
            return Page.empty(logOperationDTO.getPageable());
        }
        return getLogOperations(logOperationDTO, query);
    }

    private Page<LogOperation> getLogOperations(LogOperationDTO logOperationDTO, Query query) {
        long total = mongoTemplate.count(query, LogOperation.class);

        Pageable pageable = logOperationDTO.getPageable();
        if (total == 0) {
            return Page.empty(pageable);
        }
        query.with(pageable);

        List<LogOperation> roleDOList = mongoTemplate.find(query, LogOperation.class);

        return new PageImpl<>(roleDOList, pageable, total);
    }

    /**
     * 解析查询条件
     * @param logOperationDTO 查询条件
     * @return Query(mongodb的查询条件)
     */
    private Query getQuery(LogOperationDTO logOperationDTO, String currentUsername, String currentUserId, boolean isAdministrators) {
        Query query = new Query();
        String excludeUsername = logOperationDTO.getExcludeUsername();
        String username = logOperationDTO.getUsername();
        if (!CharSequenceUtil.isBlank(excludeUsername) && CharSequenceUtil.isBlank(username)) {
            query.addCriteria(Criteria.where("username").nin(currentUsername));
        }
        if (!CharSequenceUtil.isBlank(username)) {
            query.addCriteria(Criteria.where("username").is(username));
        }
        String ip = logOperationDTO.getIp();
        if (!CharSequenceUtil.isBlank(ip)) {
            query.addCriteria(Criteria.where("ip").is(ip));
        }
        String type = logOperationDTO.getType();
        if (!CharSequenceUtil.isBlank(type)) {
            query.addCriteria(Criteria.where("type").is(type));
        }
        if (!isAdministrators && LogOperation.Type.OPERATION_FILE.name().equals(logOperationDTO.getType())) {
            query.addCriteria(Criteria.where("fileUserId").is(currentUserId));
        }
        Long startTime = logOperationDTO.getStartTime();
        Long endTime = logOperationDTO.getEndTime();
        if (startTime != null && endTime != null) {
            LocalDateTime s = TimeUntils.getLocalDateTime(startTime);
            LocalDateTime e = TimeUntils.getLocalDateTime(endTime);
            query.addCriteria(Criteria.where(Constants.CREATE_TIME).gte(s).lte(e));
        }
        return query;
    }

    private Query getFileOperationHistoryQuery(String fileId, String requestUserId, String requestUsername) {
        Query fileQuery = new Query();
        fileQuery.addCriteria(Criteria.where("_id").is(fileId));
        fileQuery.fields().include("name", "path", "userId");
        FileDocument fileDocument = mongoTemplate.findOne(fileQuery, FileDocument.class);
        if (fileDocument == null) {
            return null;
        }
        String fileUserId = fileDocument.getUserId();
        // 构造 filepath
        String filepath = fileDocument.getPath() + fileDocument.getName();
        // 构造第二个 filepath（去掉开头的斜杠，模拟 "新建文件夹/新建文件夹/新建文件夹/未命名文件.txt"）
        String filepathWithoutSlash = fileDocument.getPath().replaceFirst("^/", "") + fileDocument.getName();

        Query query = new Query();
        query.addCriteria(Criteria.where("fileUserId").is(fileUserId));
        // 创建 $or 条件
        Criteria orCriteria = new Criteria().orOperator(
                Criteria.where("filepath").is(filepath), // filepath 精确匹配第一个路径
                Criteria.where("filepath").regex("^" + Pattern.quote(filepath)), // filepath 正则匹配（以 filepath 开头）
                Criteria.where("filepath").is(filepathWithoutSlash), // filepath 精确匹配第二个路径（无开头的斜杠）
                Criteria.where("operationFun").regex(Pattern.quote(filepath) + "\"$") // operationFun 正则匹配（以 filepath+" 结尾）
        );
        if (!fileUserId.equals(requestUserId)) {
            // 如果文件不是自己则只能看自己的操作
            query.addCriteria(Criteria.where("username").is(requestUsername));
        }
        query.addCriteria(orCriteria);
        query.addCriteria(Criteria.where("type").is(LogOperation.Type.OPERATION_FILE.name()));
        query.with(Sort.by(Sort.Direction.DESC, "createTime"));
        return query;
    }
}

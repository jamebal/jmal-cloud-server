package com.jmal.clouddisk.service.impl;

import cn.hutool.core.convert.impl.URLConverter;
import cn.hutool.core.lang.Console;
import cn.hutool.core.net.URLDecoder;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.LogOperationDTO;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TimeUntils;
import io.milton.http.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jmal
 * @Description 日志服务
 * @Date 2021/2/5 5:43 下午
 */
@Service
public class LogService {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserServiceImpl userService;

    /***
     * 存储操作日志前
     * @param logOperation 操作日志 包含参数:(time,username,operationModule,operationFun,type)
     * @param result 操作方法返回值
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     */
    public void addLogBefore(LogOperation logOperation, Object result, HttpServletRequest request, HttpServletResponse response){
        // 用户
        String username = logOperation.getUsername();
        if (!StringUtils.isEmpty(username)) {
            logOperation.setShowName(userService.getShowNameByUserUsername(username));
        }
        // UserAgent
        UserAgent userAgent = UserAgentUtil.parse(request.getHeader("User-Agent"));
        if (userAgent != null){
            logOperation.setOperatingSystem(userAgent.getOs().getName());
            logOperation.setDeviceModel(userAgent.getPlatform().getName());
            logOperation.setBrowser(userAgent.getBrowser().getName() + userAgent.getVersion());
        }
        // 请求地址
        logOperation.setUrl(URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8));
        // 请求方式
        logOperation.setMethod(request.getMethod());
        // 客户端地址
        logOperation.setIp(request.getRemoteAddr());
        // 请求参数
        Map<String, String> params = new HashMap<>(16);
        Enumeration<String> enumeration = request.getParameterNames();
        while (enumeration.hasMoreElements()){
            String key = enumeration.nextElement();
            params.put(key, request.getParameter(key));
        }
        logOperation.setParams(params);
        // 返回结果
        logOperation.setStatus(0);
        ResponseResult<Object> responseResult;
        if (result == null){
            setStatus(logOperation, response);
        } else {
            try {
                responseResult = (ResponseResult<Object>)result;
                logOperation.setStatus(responseResult.getCode());
                if(responseResult.getCode() != 0){
                    logOperation.setRemarks(responseResult.getMessage().toString());
                }
            } catch (Exception e) {
                setStatus(logOperation, response);
            }
        }
        ThreadUtil.execute(() -> addLog(logOperation));
    }

    private void setStatus(LogOperation logOperation, HttpServletResponse response) {
        if (response != null){
            int status = response.getStatus();
            if(status >= Response.Status.SC_BAD_REQUEST.code){
                logOperation.setStatus(-1);
            }
            logOperation.setRemarks(status+"");
        }
    }

    public void addLog(LogOperation logOperation){
        logOperation.setCreateTime(LocalDateTime.now());
        mongoTemplate.save(logOperation);
    }

    public ResponseResult<List<LogOperation>> list(LogOperationDTO logOperationDTO) {
        Query query = getQuery(logOperationDTO);
        List<LogOperation> logOperationList = getLogList(logOperationDTO, query);
        long count = mongoTemplate.count(query, LogOperation.class);
        return ResultUtil.success(logOperationList).setCount(count);
    }

    /***
     * 日志列表
     * @param logOperationDTO 查询条件
     */
    private List<LogOperation> getLogList(LogOperationDTO logOperationDTO, Query query) {
        setPage(logOperationDTO, query);
        setSort(logOperationDTO, query);
        return mongoTemplate.find(query, LogOperation.class);
    }

    /***
     * 解析查询条件
     * @param logOperationDTO 查询条件
     * @return Query(mongodb的查询条件)
     */
    private Query getQuery(LogOperationDTO logOperationDTO) {
        Query query = new Query();
        String username = logOperationDTO.getUsername();
        if (!StringUtils.isEmpty(username)) {
            query.addCriteria(Criteria.where("username").is(username));
        }
        String operationModule = logOperationDTO.getOperationModule();
        if (!StringUtils.isEmpty(operationModule)) {
            query.addCriteria(Criteria.where("operationModule").is(operationModule));
        }
        String type = logOperationDTO.getType();
        if (!StringUtils.isEmpty(type)) {
            query.addCriteria(Criteria.where("type").is(type));
        }
        Long startTime = logOperationDTO.getStartTime();
        Long endTime = logOperationDTO.getEndTime();
        if (startTime != null && endTime != null){
            LocalDateTime s = TimeUntils.getLocalDateTime(startTime);
            LocalDateTime e = TimeUntils.getLocalDateTime(endTime);
            query.addCriteria(Criteria.where("createTime").gte(s).lte(e));
        }
        return query;
    }

    /***
     * 设置排序
     */
    private void setSort(LogOperationDTO logOperationDTO, Query query) {
        String sortableProp = logOperationDTO.getSortProp();
        String order = logOperationDTO.getSortOrder();
        if (StringUtils.isEmpty(sortableProp) || StringUtils.isEmpty(order)){
            query.with(new Sort(Sort.Direction.DESC, "createTime"));
            return;
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if ("descending".equals(order)) {
            direction = Sort.Direction.DESC;
        }
        query.with(new Sort(direction, sortableProp));
    }

    /***
     * 设置分页条件
     */
    public void setPage(LogOperationDTO logOperationDTO, Query query) {
        Integer pageSize = logOperationDTO.getPageSize(), pageIndex = logOperationDTO.getPage();
        if (pageSize == null) {
            pageSize = 10;
        }
        if (pageIndex == null) {
            pageIndex = 1;
        }
        long skip = (pageIndex - 1) * pageSize;
        query.skip(skip);
        query.limit(pageSize);
    }
}

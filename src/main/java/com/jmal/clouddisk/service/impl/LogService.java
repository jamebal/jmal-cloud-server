package com.jmal.clouddisk.service.impl;

import cn.hutool.core.net.URLDecoder;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.LogOperationDTO;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TimeUntils;
import io.milton.http.Response;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @author jmal
 * @Description 日志服务
 * @Date 2021/2/5 5:43 下午
 */
@Service
@Slf4j
public class LogService {

    private static final int REGION_LENGTH = 5;

    private static final String REGION_DEFAULT = "0";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserLoginHolder userLoginHolder;

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private FileProperties fileProperties;

    private DbSearcher ipSearcher = null;

    @PostConstruct
    public void initIpDbSearcher() throws DbMakerConfigException, FileNotFoundException {
        String ip2regionDbPath = fileProperties.getIp2regionDbPath();
        if (!CharSequenceUtil.isBlank(ip2regionDbPath)) {
            ipSearcher = new DbSearcher(new DbConfig(), ip2regionDbPath);
        }
    }

    /***
     * 存储操作日志前
     * @param logOperation 操作日志 包含参数:(time,username,operationModule,operationFun,type)
     * @param result 操作方法返回值
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     */
    @SuppressWarnings("unchecked")
    public void addLogBefore(LogOperation logOperation, Object result, HttpServletRequest request, HttpServletResponse response) {
        // 用户
        String username = logOperation.getUsername();
        if (!CharSequenceUtil.isBlank(username)) {
            logOperation.setShowName(userService.getShowNameByUserUsername(username));
        }
        // UserAgent
        UserAgent userAgent = UserAgentUtil.parse(request.getHeader("User-Agent"));
        if (userAgent != null) {
            logOperation.setOperatingSystem(userAgent.getOs().getName());
            logOperation.setDeviceModel(userAgent.getPlatform().getName());
            logOperation.setBrowser(userAgent.getBrowser().getName() + userAgent.getVersion());
        }
        // 请求地址
        logOperation.setUrl(URLDecoder.decode(request.getRequestURI(), StandardCharsets.UTF_8));
        // 请求方式
        logOperation.setMethod(request.getMethod());
        // 客户端ip
        String ip = getIpAddress(request);
        logOperation.setIp(ip);
        if (Util.isIpAddress(ip)) {
            setIpInfo(logOperation, ip);
        }
        // 返回结果
        logOperation.setStatus(0);
        ResponseResult<Object> responseResult;
        if (result == null) {
            setStatus(logOperation, response);
        } else {
            try {
                responseResult = (ResponseResult<Object>) result;
                logOperation.setStatus(responseResult.getCode());
                if (responseResult.getCode() != 0) {
                    logOperation.setRemarks(responseResult.getMessage().toString());
                }
            } catch (Exception e) {
                setStatus(logOperation, response);
            }
        }
        ThreadUtil.execute(() -> addLog(logOperation));
    }

    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (!CharSequenceUtil.isBlank(ip)) {
            // 多次反向代理后会有多个ip值，第一个ip才是真实ip
            if (ip.contains(",")) {
                ip = ip.split(",")[0];
            }
        }
        if (CharSequenceUtil.isBlank(ip)) {
            ip = request.getHeader("X-real-ip");
        }
        if (CharSequenceUtil.isBlank(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (CharSequenceUtil.isBlank(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (CharSequenceUtil.isBlank(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (CharSequenceUtil.isBlank(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (CharSequenceUtil.isBlank(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (CharSequenceUtil.isBlank(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /***
     * 设置IP详细信息
     * @param logOperation LogOperation
     */
    private void setIpInfo(LogOperation logOperation, String ip) {
        if (ipSearcher != null) {
            try {
                DataBlock dataBlock = ipSearcher.memorySearch(ip);
                if (dataBlock != null) {
                    logOperation.setCityIp(dataBlock.getCityId());
                    logOperation.setIpInfo(region2IpInfo(dataBlock.getRegion()));
                }
            } catch (IOException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    /***
     * 解析IP区域信息
     */
    public LogOperation.IpInfo region2IpInfo(String region) {
        LogOperation.IpInfo ipInfo = new LogOperation.IpInfo();
        String[] r = region.split("\\|");
        if (r.length != REGION_LENGTH) return ipInfo;
        String country = r[0];
        if (!REGION_DEFAULT.equals(country)) {
            ipInfo.setCountry(country);
        }
        String area = r[1];
        if (!REGION_DEFAULT.equals(area)) {
            ipInfo.setArea(area);
        }
        String province = r[2];
        if (!REGION_DEFAULT.equals(province)) {
            ipInfo.setProvince(province);
        }
        String city = r[3];
        if (!REGION_DEFAULT.equals(city)) {
            ipInfo.setCity(city);
        }
        String operators = r[4];
        if (!REGION_DEFAULT.equals(operators)) {
            ipInfo.setOperators(operators);
        }
        return ipInfo;
    }

    private void setStatus(LogOperation logOperation, HttpServletResponse response) {
        if (response != null) {
            int status = response.getStatus();
            if (status >= Response.Status.SC_BAD_REQUEST.code) {
                logOperation.setStatus(-1);
            }
            logOperation.setRemarks(status + "");
        }
    }

    public void addLog(LogOperation logOperation) {
        logOperation.setCreateTime(LocalDateTime.now(TimeUntils.ZONE_ID));
        mongoTemplate.save(logOperation);
    }

    public ResponseResult<List<LogOperation>> list(LogOperationDTO logOperationDTO) {
        Query query = getQuery(logOperationDTO);
        long count = mongoTemplate.count(query, LogOperation.class);
        List<LogOperation> logOperationList = getLogList(logOperationDTO, query);
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
        String excludeUsername = logOperationDTO.getExcludeUsername();
        if (!CharSequenceUtil.isBlank(excludeUsername)) {
            query.addCriteria(Criteria.where("username").nin(userLoginHolder.getUsername()));
        }
        String username = logOperationDTO.getUsername();
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
        Long startTime = logOperationDTO.getStartTime();
        Long endTime = logOperationDTO.getEndTime();
        if (startTime != null && endTime != null) {
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
        if (CharSequenceUtil.isBlank(sortableProp) || CharSequenceUtil.isBlank(order)) {
            query.with(Sort.by(Sort.Direction.DESC, "createTime"));
            return;
        }
        Sort.Direction direction = Sort.Direction.ASC;
        if ("descending".equals(order)) {
            direction = Sort.Direction.DESC;
        }
        query.with(Sort.by(direction, sortableProp));
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
        long skip = (long) (pageIndex - 1) * pageSize;
        query.skip(skip);
        query.limit(pageSize);
    }

    /***
     * 获取url的访问次数
     * @param url url
     * @return 访问次数
     */
    public long getVisitsByUrl(String url) {
        Query query = new Query();
        query.addCriteria(Criteria.where("url").is(url));
        return mongoTemplate.count(query, LogOperation.class);
    }
}

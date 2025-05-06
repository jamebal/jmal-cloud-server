package com.jmal.clouddisk.service.impl;

import cn.hutool.core.net.URLDecoder;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.model.FileDocument;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.LogOperationDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TimeUntils;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author jmal
 * @Description 日志服务
 * @Date 2021/2/5 5:43 下午
 */
@Service
@Slf4j
public class LogService {

    private static final int REGION_LENGTH = 5;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private UserLoginHolder userLoginHolder;

    @Autowired
    private UserServiceImpl userService;

    @Autowired
    private FileProperties fileProperties;

    private Searcher ipSearcher = null;

    @PostConstruct
    public void initIpDbSearcher() {
        String ip2regionDbPath = fileProperties.getIp2regionDbPath();
        byte[] vIndex;
        try {
            vIndex = Searcher.loadVectorIndexFromFile(ip2regionDbPath);
        } catch (Exception e) {
            log.error("failed to load vector index from {}\n", ip2regionDbPath, e);
            return;
        }
        try {
            ipSearcher = Searcher.newWithVectorIndex(ip2regionDbPath, vIndex);
        } catch (Exception e) {
            log.error("failed to create vectorIndex cached searcher with {}\n", ip2regionDbPath, e);
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
        logOperation = getLogOperation(request, logOperation);
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
        asyncAddLog(logOperation);
    }

    public LogOperation getLogOperation() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return new LogOperation();
        }
        HttpServletRequest request = attributes.getRequest();
        return getLogOperation(request, null);
    }

    public LogOperation getLogOperation(HttpServletRequest request, LogOperation logOperation) {
        if (logOperation == null) {
            logOperation = new LogOperation();
            // 用户
            String username = userLoginHolder.getUsername();
            if (!CharSequenceUtil.isBlank(username)) {
                logOperation.setShowName(userService.getShowNameByUserUsername(username));
            }
            logOperation.setUsername(username);
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
        setIpInfo(logOperation, ip);
        return logOperation;
    }

    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getRemoteHost();
        if (CharSequenceUtil.isNotBlank(ip)) {
            return ip;
        }
        ip = request.getHeader("x-forwarded-for");
        if (!CharSequenceUtil.isBlank(ip) && (ip.contains(","))) {
            // 多次反向代理后会有多个ip值，第一个ip才是真实ip
            ip = ip.split(",")[0];
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
        return ip;
    }

    /***
     * 设置IP详细信息
     * @param logOperation LogOperation
     */
    private void setIpInfo(LogOperation logOperation, String ip) {
        if (ipSearcher != null && !CharSequenceUtil.isBlank(ip)) {
            try {
                String region = ipSearcher.search(ip);
                if (!CharSequenceUtil.isBlank(region)) {
                    logOperation.setIpInfo(region2IpInfo(region));
                }
            } catch (Exception ignored) {
                // log
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
        if (!Constants.REGION_DEFAULT.equals(country)) {
            ipInfo.setCountry(country);
        }
        String area = r[1];
        if (!Constants.REGION_DEFAULT.equals(area)) {
            ipInfo.setArea(area);
        }
        String province = r[2];
        if (!Constants.REGION_DEFAULT.equals(province)) {
            ipInfo.setProvince(province);
        }
        String city = r[3];
        if (!Constants.REGION_DEFAULT.equals(city)) {
            ipInfo.setCity(city);
        }
        String operators = r[4];
        if (!Constants.REGION_DEFAULT.equals(operators)) {
            ipInfo.setOperators(operators);
        }
        return ipInfo;
    }

    private void setStatus(LogOperation logOperation, HttpServletResponse response) {
        if (response != null) {
            int status = response.getStatus();
            if (status >= HttpServletResponse.SC_BAD_REQUEST) {
                logOperation.setStatus(-1);
            }
            logOperation.setRemarks(String.valueOf(status));
        }
    }

    /**
     * 添加文件操作日志(异步)
     * @param logOperation 日志
     * @param fileUsername 文件所属用户CommonFileService
     * @param filepath 文件路径
     * @param desc 描述
     */
    public void asyncAddLogFileOperation(LogOperation logOperation, String fileUsername, String filepath, String desc) {
        Completable.fromAction(() -> addLogFileOperation(logOperation, fileUsername, filepath, desc)).subscribeOn(Schedulers.io()).subscribe();
    }

    /**
     * 添加文件操作日志(同步)
     * @param logOperation 日志
     * @param fileUsername 文件所属用户CommonFileService
     * @param filepath 文件路径
     * @param desc 描述
     */
    public void syncAddLogFileOperation(LogOperation logOperation, String fileUsername, String filepath, String desc) {
        addLogFileOperation(logOperation, fileUsername, filepath, desc);
    }

    private void addLogFileOperation(LogOperation logOperation, String fileUsername, String filepath, String desc) {
        String fileUserId = userService.getUserIdByUserName(fileUsername);
        logOperation.setFileUserId(fileUserId);
        String affiliated = !fileUsername.equals(logOperation.getUsername()) ? ", 所属用户: \"" + fileUsername + "\"" : "";
        // 判断filepath开头是否为/, 不是则添加/
        if (!filepath.startsWith("/")) {
            filepath = "/" + filepath;
        }
        logOperation.setFilepath(filepath + affiliated);
        logOperation.setOperationFun(desc);
        logOperation.setType(LogOperation.Type.OPERATION_FILE.name());
        logOperation.setStatus(0);
        logOperation.setOperationModule("文件管理");
        addLog(logOperation);
    }

    /**
     * 添加文件操作日志(异步)
     * @param fileUsername 文件所属用户
     * @param filepath 文件路径
     * @param desc 描述
     */
    public void asyncAddLogFileOperation(String fileUsername, String filepath, String desc) {
        asyncAddLogFileOperation(getLogOperation(), fileUsername, filepath, desc);
    }

    /**
     * 添加文件操作日志(同步)
     * @param fileUsername 文件所属用户
     * @param filepath 文件路径
     * @param desc 描述
     */
    public void syncAddLogFileOperation(String fileUsername, String filepath, String desc) {
        syncAddLogFileOperation(getLogOperation(), fileUsername, filepath, desc);
    }

    public void asyncAddLog(LogOperation logOperation) {
        ThreadUtil.execute(() -> {
            logOperation.setCreateTime(LocalDateTime.now(TimeUntils.ZONE_ID));
            mongoTemplate.save(logOperation);
        });
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

    public ResponseResult<List<LogOperationDTO>> getFileOperationHistory(LogOperationDTO logOperationDTO, String fileId) {
        List<LogOperationDTO> logOperationDTOList;
        Query query = getFileOperationHistoryQuery(fileId);
        if (query == null) {
            logOperationDTOList = Collections.emptyList();
            return ResultUtil.success(logOperationDTOList).setCount(0);
        }
        long count = mongoTemplate.count(query, LogOperation.class);
        List<LogOperation> logOperationList = getLogList(logOperationDTO, query);
        // 加入userId
        logOperationDTOList = logOperationList.parallelStream().map(logOperation -> {
            LogOperationDTO fileOperationLog = new LogOperationDTO();
            fileOperationLog.setShowName(userService.getShowNameByUserUsername(logOperation.getUsername()));
            fileOperationLog.setAvatar(userService.getAvatarByUsername(logOperation.getUsername()));
            fileOperationLog.setCreateTime(logOperation.getCreateTime());
            fileOperationLog.setOperationFun(logOperation.getOperationFun());
            return fileOperationLog;
        }).collect(Collectors.toList());
        return ResultUtil.success(logOperationDTOList).setCount(count);
    }

    private Query getFileOperationHistoryQuery(String fileId) {
        Query fileQuery = new Query();
        fileQuery.addCriteria(Criteria.where("_id").is(fileId));
        fileQuery.fields().include("name", "path", "userId");
        FileDocument fileDocument = mongoTemplate.findOne(fileQuery, FileDocument.class);
        if (fileDocument == null) {
            return null;
        }
        String fileUserId = fileDocument.getUserId();
        String requestUserId = userLoginHolder.getUserId();
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
            query.addCriteria(Criteria.where("username").is(userService.getUserNameById(requestUserId)));
        }
        query.addCriteria(orCriteria);
        query.addCriteria(Criteria.where("type").is(LogOperation.Type.OPERATION_FILE.name()));
        query.with(Sort.by(Sort.Direction.DESC, "createTime"));
        return query;
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
        String username = logOperationDTO.getUsername();
        if (!CharSequenceUtil.isBlank(excludeUsername) && CharSequenceUtil.isBlank(username)) {
            query.addCriteria(Criteria.where("username").nin(userLoginHolder.getUsername()));
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
        ConsumerDO consumerDO = userService.getUserInfoByUsername(userLoginHolder.getUsername());
        if ((consumerDO.getCreator() == null || !consumerDO.getCreator()) && LogOperation.Type.OPERATION_FILE.name().equals(logOperationDTO.getType())) {
            query.addCriteria(Criteria.where("fileUserId").is(userLoginHolder.getUserId()));
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

    /***
     * 设置排序
     */
    private void setSort(LogOperationDTO logOperationDTO, Query query) {
        String sortableProp = logOperationDTO.getSortProp();
        String order = logOperationDTO.getSortOrder();
        if (CharSequenceUtil.isBlank(sortableProp) || CharSequenceUtil.isBlank(order)) {
            query.with(Sort.by(Sort.Direction.DESC, Constants.CREATE_TIME));
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
        Integer pageSize = logOperationDTO.getPageSize();
        Integer pageIndex = logOperationDTO.getPage();
        CommonFileService.setPage(pageSize, pageIndex, query);
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

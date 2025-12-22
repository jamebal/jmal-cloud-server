package com.jmal.clouddisk.service.impl;

import cn.hutool.core.net.URLDecoder;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.jmal.clouddisk.config.FileProperties;
import com.jmal.clouddisk.dao.ILogDAO;
import com.jmal.clouddisk.model.LogOperation;
import com.jmal.clouddisk.model.LogOperationDTO;
import com.jmal.clouddisk.model.rbac.ConsumerDO;
import com.jmal.clouddisk.service.Constants;
import com.jmal.clouddisk.util.IPUtil;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import com.jmal.clouddisk.util.TimeUntils;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jmal
 * @Description 日志服务
 * @Date 2021/2/5 5:43 下午
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LogService {

    private final ILogDAO logDAO;

    private final UserLoginHolder userLoginHolder;

    private final CommonUserService userService;

    private final RoleService roleService;

    private final FileProperties fileProperties;

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
            logOperation.setShowName(getShowNameByUserUsername(username));
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
                    logOperation.setRemarks(responseResult.getMessage());
                }
            } catch (Exception e) {
                setStatus(logOperation, response);
            }
        }
        asyncAddLog(logOperation);
    }

    public String getShowNameByUserUsername(String username) {
        ConsumerDO consumer = userService.getUserInfoByUsername(username);
        if (consumer == null) {
            return "";
        }
        return consumer.getShowName();
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
                logOperation.setShowName(getShowNameByUserUsername(username));
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
        String ip = IPUtil.getClientIP(request);
        logOperation.setIp(ip);
        setIpInfo(logOperation, ip);
        return logOperation;
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

    /**
     * 解析IP区域信息, region格式: 国家|区域|省份|城市|运营商
     */
    public LogOperation.IpInfo region2IpInfo(String region) {
        LogOperation.IpInfo ipInfo = new LogOperation.IpInfo();

        if (region == null || region.isEmpty()) {
            return ipInfo;
        }

        String[] parts = IPUtil.SPLIT_PATTERN.split(region, IPUtil.REGION_LENGTH);

        if (parts.length != IPUtil.REGION_LENGTH) {
            return ipInfo;
        }

        String def = Constants.REGION_DEFAULT;

        if (!def.equals(parts[0])) ipInfo.setCountry(parts[0]);
        if (!def.equals(parts[1])) ipInfo.setArea(parts[1]);
        if (!def.equals(parts[2])) ipInfo.setProvince(parts[2]);
        if (!def.equals(parts[3])) ipInfo.setCity(parts[3]);
        if (!def.equals(parts[4])) ipInfo.setOperators(parts[4]);

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
        Completable.fromAction(() -> addLogFileOperation(logOperation, fileUsername, filepath, desc)).subscribeOn(Schedulers.io())
                .doOnError(e -> log.error(e.getMessage(), e))
                .onErrorComplete()
                .subscribe();
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
        Completable.fromAction(() -> {
            logOperation.setCreateTime(LocalDateTime.now(TimeUntils.ZONE_ID));
            logDAO.save(logOperation);
        }).subscribeOn(Schedulers.io())
                .doOnError(e -> log.error(e.getMessage(), e))
                .onErrorComplete()
                .subscribe();
    }

    public void addLog(LogOperation logOperation) {
        logOperation.setCreateTime(LocalDateTime.now(TimeUntils.ZONE_ID));
        logDAO.save(logOperation);
    }

    public ResponseResult<List<LogOperation>> list(LogOperationDTO logOperationDTO) {
        String currentUsername = userLoginHolder.getUsername();
        String currentUserId = userLoginHolder.getUserId();
        boolean isAdministrators = roleService.isAdministratorsByUserId(currentUserId);
        Page<LogOperation> page = logDAO.findAllByQuery(logOperationDTO, currentUsername, currentUserId, isAdministrators);
        return ResultUtil.success(page.getContent()).setCount(page.getTotalElements());
    }

    public ResponseResult<List<LogOperationDTO>> getFileOperationHistory(LogOperationDTO logOperationDTO, String fileId) {
        String currentUserId = userLoginHolder.getUserId();
        String currentUsername = userLoginHolder.getUsername();
        Page<LogOperation> page = logDAO.findFileOperationHistoryByFileId(logOperationDTO, fileId, currentUserId, currentUsername);
        // 加入userId
        List<LogOperationDTO> logOperationDTOList = page.getContent().parallelStream().map(logOperation -> {
            LogOperationDTO fileOperationLog = new LogOperationDTO();
            fileOperationLog.setShowName(getShowNameByUserUsername(logOperation.getUsername()));
            fileOperationLog.setAvatar(userService.getAvatarByUsername(logOperation.getUsername()));
            fileOperationLog.setCreateTime(logOperation.getCreateTime());
            fileOperationLog.setOperationFun(logOperation.getOperationFun());
            return fileOperationLog;
        }).collect(Collectors.toList());
        return ResultUtil.success(logOperationDTOList).setCount(page.getTotalElements());
    }

    /***
     * 获取url的访问次数
     * @param url url
     * @return 访问次数
     */
    public long getVisitsByUrl(String url) {
        return logDAO.countByUrl(url);
    }

    @PreDestroy
    public void destroy() {
        if (ipSearcher != null) {
            try {
                ipSearcher.close();
            } catch (IOException e) {
                log.error("failed to close ip searcher", e);
            }
        }
    }
}

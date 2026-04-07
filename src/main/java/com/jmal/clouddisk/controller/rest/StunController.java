package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.stun.StunChannelUpdateDTO;
import com.jmal.clouddisk.model.stun.StunGostNode;
import com.jmal.clouddisk.model.stun.StunGostNodesQuery;
import com.jmal.clouddisk.service.impl.StunChannelService;
import com.jmal.clouddisk.util.ResponseResult;
import com.jmal.clouddisk.util.ResultUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "STUN 通道管理")
@RequestMapping("stun")
@RequiredArgsConstructor
public class StunController {

    private final StunChannelService stunChannelService;

    @Operation(summary = "更新 STUN 通道地址")
    @PostMapping(value = "/{channelId}/update", produces = MediaType.APPLICATION_JSON_VALUE)
    @Permission("cloud:set:sync")
    @LogOperatingFun
    public ResponseResult<Object> updateChannelAddress(@PathVariable String channelId,
                                                       @RequestBody(required = false) StunChannelUpdateDTO requestBody) {
        stunChannelService.updateChannelAddress(channelId, requestBody == null ? null : requestBody.getAddr());
        return ResultUtil.success();
    }

    @Operation(summary = "获取 STUN 通道最新地址")
    @GetMapping(value = "/{channelId}/get", produces = MediaType.TEXT_PLAIN_VALUE)
    @Permission("cloud:set:sync")
    public ResponseEntity<String> getChannelAddress(@PathVariable String channelId) {
        return stunChannelService.getChannelAddress(channelId)
                .map(address -> ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(address))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Operation(summary = "获取 STUN 通道最新地址(JSON)")
    @GetMapping(value = "/{channelId}/address", produces = MediaType.APPLICATION_JSON_VALUE)
    @Permission("cloud:set:sync")
    public ResponseResult<String> getChannelAddressJson(@PathVariable String channelId) {
        return stunChannelService.getChannelAddress(channelId)
                .map(ResultUtil::success)
                .orElseGet(() -> ResultUtil.error("动态地址不存在"));
    }

    @Operation(summary = "获取适配 gost 3.x 的动态节点")
    @GetMapping(value = "/{channelId}/gost/nodes", produces = MediaType.APPLICATION_JSON_VALUE)
    @Permission("cloud:set:sync")
    public List<StunGostNode> getGostNodes(@PathVariable String channelId,
                                           @RequestParam(required = false) String username,
                                           @RequestParam(required = false) String password,
                                           @RequestParam(required = false) String connector,
                                           @RequestParam(required = false) String dialer,
                                           @RequestParam(required = false) String name,
                                           @RequestParam(required = false) String serverName,
                                           @RequestParam(required = false) String caFile,
                                           @RequestParam(required = false) String secure) {
        StunGostNodesQuery query = new StunGostNodesQuery();
        query.setUsername(username);
        query.setPassword(password);
        query.setConnector(connector);
        query.setDialer(dialer);
        query.setName(name);
        query.setServerName(serverName);
        query.setCaFile(caFile);
        query.setSecure(secure);
        return stunChannelService.getGostNodes(channelId, query);
    }
}

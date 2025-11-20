package com.jmal.clouddisk.controller.rest;

import com.jmal.clouddisk.annotation.LogOperatingFun;
import com.jmal.clouddisk.annotation.Permission;
import com.jmal.clouddisk.model.query.QueryGroupDTO;
import com.jmal.clouddisk.model.rbac.GroupAssignDTO;
import com.jmal.clouddisk.model.rbac.GroupDTO;
import com.jmal.clouddisk.service.impl.GroupService;
import com.jmal.clouddisk.util.ResponseResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/**
 * @author jmal
 * @Description 用户组管理 Controller
 */
@RestController
@RequestMapping("group")
@Tag(name = "用户组管理")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @Operation(summary = "用户组列表")
    @GetMapping("/list")
    @Permission("sys:group:list")
    public ResponseResult<List<GroupDTO>> list(QueryGroupDTO queryDTO) {
        return groupService.list(queryDTO);
    }

    @Operation(summary = "用户组详情")
    @GetMapping("/info")
    @Permission("sys:group:list")
    public ResponseResult<GroupDTO> info(@RequestParam String id) {
        return groupService.info(id);
    }

    @Operation(summary = "添加用户组")
    @PostMapping("/add")
    @Permission("sys:group:add")
    @LogOperatingFun
    public ResponseResult<Object> add(@RequestBody @Validated GroupDTO groupDTO) {
        return groupService.add(groupDTO);
    }

    @Operation(summary = "修改用户组")
    @PutMapping("/update")
    @Permission("sys:group:update")
    @LogOperatingFun
    public ResponseResult<Object> update(@RequestBody @Validated GroupDTO groupDTO) {
        return groupService.update(groupDTO);
    }

    @Operation(summary = "删除用户组")
    @DeleteMapping("/delete")
    @Permission("sys:group:delete")
    @LogOperatingFun
    public ResponseResult<Object> delete(@RequestParam String[] ids) {
        return groupService.delete(Arrays.asList(ids));
    }

    @Operation(summary = "获取组内用户名列表")
    @GetMapping("/assigned-users")
    @Permission("sys:group:list")
    public ResponseResult<List<String>> getAssignedUsers(@RequestParam String groupId) {
        return groupService.getAssignedUsernames(groupId);
    }

    @Operation(summary = "分配用户")
    @PostMapping("/assign-users")
    @Permission("sys:group:update")
    @LogOperatingFun
    public ResponseResult<Object> assignUsers(@RequestBody @Validated GroupAssignDTO assignDTO) {
        return groupService.assignUsers(assignDTO);
    }
}

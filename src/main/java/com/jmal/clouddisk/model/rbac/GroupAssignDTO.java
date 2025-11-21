package com.jmal.clouddisk.model.rbac;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "用户组分配用户请求对象")
public class GroupAssignDTO {
    @NotNull(message = "用户组ID不能为空")
    @Schema(description = "用户组ID")
    private String groupId;

    @Schema(description = "用户名列表")
    private List<String> usernameList;
}

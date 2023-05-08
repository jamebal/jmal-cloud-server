package com.jmal.clouddisk.model.rbac;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @Description ConsumerBase
 * @blame jmal
 * @Date 2022/8/20 23:21
 */
@Data
public class ConsumerBase {
    @Schema(name = "password", title = "密码", example = "123456")
    String password;
}

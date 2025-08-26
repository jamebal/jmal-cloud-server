package com.jmal.clouddisk.model.rbac;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.config.jpa.AuditableEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@MappedSuperclass
public class ConsumerBase extends AuditableEntity implements Reflective {

    @Schema(name = "password", title = "密码", example = "123456")
    String password;
}

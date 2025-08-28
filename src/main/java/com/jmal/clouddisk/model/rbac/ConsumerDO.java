package com.jmal.clouddisk.model.rbac;

import com.jmal.clouddisk.config.Reflective;
import com.jmal.clouddisk.service.impl.UserServiceImpl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * @Description 用户模型
 * @author jmal
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Schema
@RegisterReflectionForBinding
@Document(collection = UserServiceImpl.COLLECTION_NAME)
@Entity
@Table(name = UserServiceImpl.COLLECTION_NAME)
public class ConsumerDO extends ConsumerBase implements Reflective {
    @Schema(name = "username", title = "用户名", example = "admin")
    @Indexed
    String username;
    @Indexed
    @Schema(name = "showName", title = "显示用户名", example = "管理员1")
    String showName;
    @Schema(title = "头像", example = "https://wpimg.wallstcn.com/f778738c-e4f8-4870-b634-56703b4acafe.gif")
    String avatar;
    @Schema(name = "slogan", title = "标语")
    String slogan;
    @Schema(name = "introduction", title = "简介")
    String introduction;
    @Schema(name = "webpDisabled", title = "是否禁用webp")
    Boolean webpDisabled;

    /**
     * 角色ID列表
     * 存储格式：["66cb6e9c507f4a2b8c1d3e5f", "66cb6e9c507f4a2b8c1d3e60"]
     */
    @Column(name = "roles", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    List<String> roles;

    @Schema(name = "quota", title = "默认配额, 10G", example = "10")
    Integer quota;
    @Schema(name = "takeUpSpace", title = "已使用的空间")

    Long takeUpSpace;
    @Schema(name = "creator", title = "网盘创建者", hidden = true)
    Boolean creator;

    @Schema(name = "mfaEnabled", title = "mfa_enabled")
    Boolean mfaEnabled;

    @Schema(name = "mfaSecret", title = "encrypted_mfa_secret")
    @Column(columnDefinition = "TEXT")
    String mfaSecret;

}

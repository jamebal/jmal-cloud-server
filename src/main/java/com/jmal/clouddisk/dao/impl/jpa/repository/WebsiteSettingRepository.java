package com.jmal.clouddisk.dao.impl.jpa.repository;

import com.jmal.clouddisk.config.jpa.RelationalDataSourceCondition;
import com.jmal.clouddisk.model.WebsiteSettingDO;
import org.springframework.context.annotation.Conditional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@Conditional(RelationalDataSourceCondition.class)
public interface WebsiteSettingRepository extends JpaRepository<WebsiteSettingDO, String> {

    @Query("select w from WebsiteSettingDO w")
    Optional<WebsiteSettingDO> findOne();

    /**
     * 更新网盘Logo
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET w.netdiskLogo = :logo")
    int updateNetdiskLogo(@Param("logo") String logo);

    /**
     * 更新网盘名称
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET w.netdiskName = :name")
    int updateNetdiskName(@Param("name") String name);

    /**
     * 更新iframe配置
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET w.iframe = :iframe")
    int updateIframe(@Param("iframe") String iframe);

    /**
     * 更新MFA强制启用设置
     */
    @Modifying
    @Query("UPDATE WebsiteSettingDO w SET w.mfaForceEnable = :mfaForceEnable")
    int updateMfaForceEnable(@Param("mfaForceEnable") Boolean mfaForceEnable);

}

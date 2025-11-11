package com.jmal.clouddisk.dao.impl.mongodb;

import cn.hutool.core.text.CharSequenceUtil;
import com.jmal.clouddisk.dao.IWebsiteSettingDAO;
import com.jmal.clouddisk.model.NetdiskPersonalization;
import com.jmal.clouddisk.model.WebsiteSettingDO;
import com.jmal.clouddisk.util.MongoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "jmalcloud.datasource.type", havingValue = "mongodb")
public class WebsiteSettingDAOImpl implements IWebsiteSettingDAO {

    private final MongoTemplate mongoTemplate;

    @Override
    public WebsiteSettingDO findOne() {
        WebsiteSettingDO websiteSettingDO = mongoTemplate.findOne(new Query(), WebsiteSettingDO.class);
        if (websiteSettingDO == null) {
            websiteSettingDO = new WebsiteSettingDO();
            mongoTemplate.save(websiteSettingDO);
        }
        return websiteSettingDO;
    }

    @Override
    public void updateLogo(String logo) {
        Update update = new Update();
        update.set("netdiskLogo", logo);
        mongoTemplate.upsert(new Query(), update, WebsiteSettingDO.class);
    }

    @Override
    public void updateName(String name) {
        Update update = new Update();
        update.set("netdiskName", name);
        mongoTemplate.upsert(new Query(), update, WebsiteSettingDO.class);
    }


    @Override
    public void updatePersonalization(NetdiskPersonalization personalization) {
        Update update = new Update();
        update.set("personalization", personalization);
        mongoTemplate.upsert(new Query(), update, WebsiteSettingDO.class);
    }

    @Override
    public void upsert(WebsiteSettingDO websiteSettingDO) {
        Update update = MongoUtil.getUpdate(websiteSettingDO);
        mongoTemplate.upsert(new Query(), update, WebsiteSettingDO.class);
    }

    @Override
    public WebsiteSettingDO getPreviewConfig() {
        Query query = new Query();
        query.fields().include("iframe");
        WebsiteSettingDO websiteSettingDO = mongoTemplate.findOne(query, WebsiteSettingDO.class);
        if (websiteSettingDO == null) {
            websiteSettingDO = new WebsiteSettingDO();
            mongoTemplate.save(websiteSettingDO);
        }
        return websiteSettingDO;
    }

    @Override
    public void updatePreviewConfig(String iframe) {
        if (CharSequenceUtil.isBlank(iframe)) {
            return;
        }
        Update update = new Update();
        update.set("iframe", iframe);
        mongoTemplate.upsert(new Query(), update, WebsiteSettingDO.class);
    }

    @Override
    public void setMfaForceEnable(Boolean mfaForceEnable) {
        Query query = new Query();
        Update update = new Update();
        update.set("mfaForceEnable", mfaForceEnable);
        mongoTemplate.upsert(query, update, WebsiteSettingDO.class);
    }

}

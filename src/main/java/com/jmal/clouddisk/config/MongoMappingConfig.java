package com.jmal.clouddisk.config;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * @Description 让mongodb中的collection去掉属性_class
 * @author jmal
 */
@Configuration
public class MongoMappingConfig extends AbstractMongoClientConfiguration {

    @Override
    protected String getDatabaseName() {
        return "jmalcloud";
    }

    @Override
    @Bean
    public MappingMongoConverter mappingMongoConverter(MongoDatabaseFactory databaseFactory, MongoCustomConversions customConversions, MongoMappingContext mappingContext){
        MappingMongoConverter mmc = super.mappingMongoConverter(databaseFactory,customConversions,mappingContext);
        mmc.setTypeMapper(defaultMongoTypeMapper());
        return mmc;
    }

    @Bean
    public MongoTypeMapper defaultMongoTypeMapper() {
        return new DefaultMongoTypeMapper(null);
    }

}

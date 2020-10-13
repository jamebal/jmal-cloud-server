package com.jmal.clouddisk.config;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDbFactory;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * @Description 让mongodb中的collection去掉属性_class
 * @author jmal
 */
@Configuration
public class MongoMappingConfig {
    @Bean
    public MappingMongoConverter mappingMongoConverter(MongoDbFactory factory,
                                                       MongoMappingContext context, BeanFactory beanFactory){
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(factory);
        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver,context);
        try{
            //指定为MongoCustomConversions,若果项目引用的redis会抛出:available: expected single matching bean but found 2: mongoCustomConversions,redisCustomConversions
            converter.setCustomConversions(beanFactory.getBean(MongoCustomConversions.class));
        }catch (Exception e){
            e.printStackTrace();
        }
        //don't save column _class to mongo collection
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));
        return converter;
    }
}

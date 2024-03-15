package com.jmal.clouddisk.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

@Configuration
public class MongoClientConfig extends AbstractMongoClientConfiguration {

  @NotNull
  @Override
  protected String getDatabaseName() {
    return "jmalcloud";
  }

  @Override
  public boolean autoIndexCreation() {
    return true;
  }

}

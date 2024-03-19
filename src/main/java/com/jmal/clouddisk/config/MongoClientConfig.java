package com.jmal.clouddisk.config;

import cn.hutool.core.util.StrUtil;
import com.mongodb.MongoClientSettings;
import com.mongodb.ServerAddress;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import static java.util.Collections.singletonList;

@Configuration
@Slf4j
public class MongoClientConfig extends AbstractMongoClientConfiguration {

  @Autowired
  Environment env;

  @NotNull
  @Override
  protected String getDatabaseName() {
    String name = "jmalcloud";
    String mongoURI = env.getProperty("spring.data.mongodb.uri");
    if (StrUtil.isBlank(mongoURI)) {
      return name;
    }
    try {
      URI uri = new URI(mongoURI);
      return uri.getPath().substring(1);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
    return name;
  }

  @Override
  public boolean autoIndexCreation() {
    return true;
  }

  private String domainResolver(String domainName) {
    if (StrUtil.isBlank(domainName)) {
      return "localhost";
    }
    try {
      InetAddress address = InetAddress.getByName(domainName);
      return address.getHostAddress();
    } catch (UnknownHostException e) {
      log.error(e.getMessage(), e);
    }
    return "localhost";
  }

  @Override
  protected void configureClientSettings(@NotNull MongoClientSettings.Builder builder) {

    String host = "mongo";
    String port = "27017";
    String mongoURI = env.getProperty("spring.data.mongodb.uri");
    if (StrUtil.isNotBlank(mongoURI)) {
        try {
            URI uri = new URI(mongoURI);
            host = uri.getHost();
            port = String.valueOf(uri.getPort());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    String finalPort = port;
    String finalHost = host;
    builder.applyToClusterSettings(settings -> {
      settings.hosts(singletonList(
              new ServerAddress(domainResolver(finalHost), Integer.parseInt(finalPort))));
    });
  }

}

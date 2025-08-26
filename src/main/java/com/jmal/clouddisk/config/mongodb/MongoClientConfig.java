package com.jmal.clouddisk.config.mongodb;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.config.AbstractMongoClientConfiguration;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.Objects;


@Configuration
@Slf4j
@RequiredArgsConstructor
public class MongoClientConfig extends AbstractMongoClientConfiguration {

  private final Environment env;

  @NotNull
  @Override
  protected String getDatabaseName() {
    String mongoURI = env.getProperty("spring.data.mongodb.uri");
    if (mongoURI != null && !mongoURI.isBlank()) {
      try {
        return Objects.requireNonNull(new ConnectionString(mongoURI).getDatabase());
      } catch (Exception e) {
        log.error("Invalid MongoDB URI", e);
      }
    }
    return "jmalcloud";
  }

  @Override
  public boolean autoIndexCreation() {
    return true;
  }

  @NotNull
  @Bean
  @Override
  public MongoClient mongoClient() {
    String mongoURI = env.getProperty("spring.data.mongodb.uri");
    if (mongoURI != null && !mongoURI.isBlank()) {
      try {
        ConnectionString connectionString = new ConnectionString(mongoURI);
        MongoClientSettings mongoClientSettings = MongoClientSettings.builder()
                .applyConnectionString(connectionString)
                .build();
        return MongoClients.create(mongoClientSettings);
      } catch (Exception e) {
        log.error("Failed to create MongoClient", e);
      }
    }
    return MongoClients.create();
  }

  @Bean
  public MongoTemplate mongoTemplate() {
    return new MongoTemplate(mongoClient(), getDatabaseName());
  }
}

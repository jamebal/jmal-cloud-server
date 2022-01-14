package com.jmal.clouddisk.swagger;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * @author jmal
 * @Description OpenApiConfig
 * @Date 2020-01-31 22:04
 */
@Configuration
public class OpenApiConfig {

    @Value("${version}")
    String version;

    @Bean
    public OpenAPI jmalCloudOpenApi() {
        return new OpenAPI()
                .info(new Info().title("JmalCloud API")
                        .description("JmalCloud application")
                        .version(version)
                        .contact(new Contact().name("jmal").url("https://blog.jmal.top").email("zhushilun084@gmail.com"))
                        .license(new License().name("MIT license").url("https://github.com/jamebal/jmal-cloud-view/blob/master/LICENSE")))
                        .externalDocs(new ExternalDocumentation());
    }
}

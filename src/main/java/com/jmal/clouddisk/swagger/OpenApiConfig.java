package com.jmal.clouddisk.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
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

    @Bean
    public OpenAPI jmalCloudOpenApi(@Value("${version}") String appVersion) {

        //在配置好的配置类中增加此段代码即可

        return new OpenAPI()
                .components(new Components().addHeaders("jmal-token", new Header().description("myHeader2 header"))
                .addParameters("myGlobalHeader", new HeaderParameter().required(true).name("jmal-token").description("").schema(new StringSchema()).required(false)))
                .info(new Info().title("JmalCloud API")
                        .description("")
                        .version(appVersion)
                        .license(new License().name("MIT license").url("https://github.com/jamebal/jmal-cloud-view/blob/master/LICENSE")))
                        .externalDocs(new ExternalDocumentation());
    }


}

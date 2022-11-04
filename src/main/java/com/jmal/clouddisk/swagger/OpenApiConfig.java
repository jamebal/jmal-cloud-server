package com.jmal.clouddisk.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import org.springdoc.core.customizers.OpenApiCustomiser;
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

        //在配置好的配置类中增加此段代码即可

        return new OpenAPI()
                .components(new Components()
                .addParameters("myGlobalHeader", new HeaderParameter().required(true).name("jmal-token").description("").schema(new StringSchema()).required(false)))
                .info(new Info().title("JmalCloud API")
                        .description("JmalCloud application")
                        .version(version)
                        .contact(new Contact().name("jmal").url("https://blog.jmal.top").email("zhushilun084@gmail.com"))
                        .license(new License().name("MIT license").url("https://github.com/jamebal/jmal-cloud-view/blob/master/LICENSE")))
                        .externalDocs(new ExternalDocumentation());
    }

    /**
     * 添加全局的请求头参数
     */
    @Bean
    public OpenApiCustomiser customerGlobalHeaderOpenApiCustomiser() {
        return openApi -> openApi.getPaths().values().stream().flatMap(pathItem -> pathItem.readOperations().stream())
                .forEach(operation -> {
                    operation.addParametersItem(new HeaderParameter().$ref("#/components/parameters/myGlobalHeader"));
                });
    }
}

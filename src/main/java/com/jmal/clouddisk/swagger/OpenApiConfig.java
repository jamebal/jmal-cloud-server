package com.jmal.clouddisk.swagger;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.io.*;


/**
 * @author jmal
 * @Description OpenApiConfig
 * @Date 2020-01-31 22:04
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI jmalCloudOpenApi() throws IOException, XmlPullParserException {

        String rootPath = System.getProperty("user.dir");
        MavenXpp3Reader reader = new MavenXpp3Reader();
        String myPom = rootPath + File.separator + "pom.xml";
        Model model = reader.read(new FileReader(myPom));
        String version = model.getVersion();

        return new OpenAPI()
                .info(new Info().title("JmalCloud API")
                        .description("JmalCloud application")
                        .version(version)
                        .contact(new Contact().name("jmal").url("https://blog.jmal.top").email("zhushilun084@gmail.com"))
                        .license(new License().name("MIT license").url("https://github.com/jamebal/jmal-cloud-view/blob/master/LICENSE")))
                        .externalDocs(new ExternalDocumentation());
    }
}

package com.company.dss;

import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.company.dss.config.DotEnvLoader;
import com.company.dss.config.DssProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(DssProperties.class)
public class DssIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DssIntegrationApplication.class);
        Map<String, Object> dotenv = DotEnvLoader.load();
        if (!dotenv.isEmpty()) {
            app.addInitializers(ctx -> ctx.getEnvironment().getPropertySources()
                    .addFirst(new MapPropertySource("dotenv", dotenv)));
        }
        app.run(args);
    }
}

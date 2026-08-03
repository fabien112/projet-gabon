package com.company.dss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.company.dss.config.DssProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(DssProperties.class)
public class DssIntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(DssIntegrationApplication.class, args);
    }
}

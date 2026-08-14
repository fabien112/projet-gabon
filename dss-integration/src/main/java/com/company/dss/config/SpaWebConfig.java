package com.company.dss.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Point d'entrée du frontend buildé (dossier ./web à côté du JAR).
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
        registry.addViewController("/sync").setViewName("forward:/index.html");
        registry.addViewController("/sync/").setViewName("forward:/index.html");
        registry.addViewController("/config").setViewName("forward:/index.html");
        registry.addViewController("/config/").setViewName("forward:/index.html");
        registry.addViewController("/lea").setViewName("forward:/index.html");
        registry.addViewController("/lea/").setViewName("forward:/index.html");
        registry.addViewController("/login").setViewName("forward:/index.html");
        registry.addViewController("/login/").setViewName("forward:/index.html");
    }
}

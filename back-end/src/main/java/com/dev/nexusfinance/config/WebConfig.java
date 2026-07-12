package com.dev.nexusfinance.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String allowedOrigin;
    public WebConfig(@Value("${app.cors.allowed-origin}") String allowedOrigin) { this.allowedOrigin = allowedOrigin; }
    @Override public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(allowedOrigin).allowedMethods("GET", "POST", "DELETE", "OPTIONS")
            .allowedHeaders("Authorization", "Content-Type");
    }
}

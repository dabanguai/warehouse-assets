package com.yingchang.warehouse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
import java.nio.file.Path;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final Auth auth;
    private final String origin;
    private final Path h5;
    public WebConfig(Auth auth, @Value("${warehouse.cors-origin}") String origin, @Value("${warehouse.h5-dir}") String h5) { this.auth = auth; this.origin = origin; this.h5 = Path.of(h5).toAbsolutePath(); }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(auth).addPathPatterns("/api/**"); }
    @Override public void addCorsMappings(CorsRegistry registry) { registry.addMapping("/api/**").allowedOrigins(origin).allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS").allowedHeaders("Authorization", "Content-Type"); }
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) { registry.addResourceHandler("/**").addResourceLocations(h5.toUri().toString() + "/"); }
    @Override public void addViewControllers(ViewControllerRegistry registry) { registry.addViewController("/").setViewName("forward:/index.html"); }
}

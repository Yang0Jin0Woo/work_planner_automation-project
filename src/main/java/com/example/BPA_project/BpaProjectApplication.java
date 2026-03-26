package com.example.BPA_project;

import com.example.BPA_project.config.AppProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class BpaProjectApplication {

    public static void main(String[] args) {
        SpringApplication.run(BpaProjectApplication.class, args);
    }
}

package com.vcs.customvcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.vcs.customvcs.config.StorageProperties;

@SpringBootApplication
// Register the storage configuration bean for constructor injection into services.
@EnableConfigurationProperties(StorageProperties.class)
public class CustomvcsApplication {
    public static void main(String[] args) {
        SpringApplication.run(CustomvcsApplication.class, args);
    }
}

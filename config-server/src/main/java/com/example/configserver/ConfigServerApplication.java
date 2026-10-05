package com.example.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Centralized, Git-backed configuration server. Every microservice pulls
 * its externalized config (application.yml + <service-name>.yml) from
 * here instead of bundling it inside the jar, so config changes don't
 * require a rebuild/redeploy.
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}

package com.aistreaming.framework;

import com.aistreaming.framework.config.StreamingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(StreamingProperties.class)
public class AiStreamingFrameworkApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiStreamingFrameworkApplication.class, args);
    }
}

package com.fileshare.config;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UuidConfig {

    @Bean
    public TimeBasedEpochGenerator uuidGenerator() {
        return Generators.timeBasedEpochGenerator();
    }
}

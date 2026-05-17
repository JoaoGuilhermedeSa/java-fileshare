package com.fileshare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fileshare.storage")
public record StorageProperties(String rootDir) {
}

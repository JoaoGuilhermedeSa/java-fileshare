package com.fileshare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fileshare.encryption")
public record EncryptionProperties(String passphrase, String salt) {
}

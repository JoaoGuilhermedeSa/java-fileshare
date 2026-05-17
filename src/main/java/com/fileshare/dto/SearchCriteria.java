package com.fileshare.dto;

import java.time.Instant;
import java.util.Map;

public record SearchCriteria(
        String q,
        String virtualDirectory,
        Map<String, String> tags,
        Instant uploadedFrom,
        Instant uploadedTo
) {
}

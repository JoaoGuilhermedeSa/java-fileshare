package com.fileshare.dto;

import com.fileshare.domain.FileMetadata;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public record FileInfoDto(
        UUID id,
        String originalName,
        String virtualDirectory,
        String contentType,
        long sizeBytes,
        String checksum,
        Instant uploadedAt,
        Instant lastModifiedAt,
        Map<String, String> tags
) {
    public static FileInfoDto from(FileMetadata m) {
        var tags = m.getTags().stream()
                .collect(Collectors.toMap(t -> t.getName(), t -> t.getValue()));
        return new FileInfoDto(
                m.getId(),
                m.getOriginalName(),
                m.getVirtualDirectory(),
                m.getContentType(),
                m.getSizeBytes(),
                m.getChecksum(),
                m.getUploadedAt(),
                m.getLastModifiedAt(),
                tags
        );
    }
}

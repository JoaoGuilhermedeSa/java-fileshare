package com.fileshare.repository;

import com.fileshare.domain.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.UUID;

public interface FileMetadataRepository
        extends JpaRepository<FileMetadata, UUID>,
                JpaSpecificationExecutor<FileMetadata> {
}

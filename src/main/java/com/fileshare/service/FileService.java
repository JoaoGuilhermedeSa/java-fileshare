package com.fileshare.service;

import com.fileshare.domain.FileMetadata;
import com.fileshare.domain.FileTag;
import com.fileshare.dto.FileInfoDto;
import com.fileshare.dto.PagedResponseDto;
import com.fileshare.dto.SearchCriteria;
import com.fileshare.exception.FileNotFoundException;
import com.fileshare.repository.FileMetadataRepository;
import com.fileshare.repository.FileSpecification;
import com.fileshare.service.storage.FileStorage;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.file.Path;

@Service
@Transactional(readOnly = true)
public class FileService {

    private final FileMetadataRepository repository;
    private final EncryptionService      encryptionService;
    private final FileStorage            fileStorage;
    private final TimeBasedEpochGenerator uuidGenerator;

    public FileService(FileMetadataRepository repository,
                       EncryptionService encryptionService,
                       FileStorage fileStorage,
                       TimeBasedEpochGenerator uuidGenerator) {
        this.repository    = repository;
        this.encryptionService = encryptionService;
        this.fileStorage   = fileStorage;
        this.uuidGenerator = uuidGenerator;
    }

    @Transactional
    public FileInfoDto upload(MultipartFile file,
                              String virtualDirectory,
                              Map<String, String> tags) throws IOException {
        var plaintext = file.getBytes();
        var checksum  = sha256Hex(plaintext);
        var encrypted = encryptionService.encrypt(plaintext);

        var id = uuidGenerator.generate();
        var storedPath = fileStorage.store(id, encrypted);

        var metadata = new FileMetadata();
        metadata.setId(id);
        metadata.setOriginalName(sanitizeFilename(file.getOriginalFilename()));
        metadata.setStoredPath(storedPath);
        metadata.setVirtualDirectory(normalizeDirectory(virtualDirectory));
        metadata.setContentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        metadata.setSizeBytes(plaintext.length);
        metadata.setChecksum(checksum);
        metadata.setEncryptionIv(encryptionService.extractIvHex(encrypted));
        metadata.setUploadedAt(Instant.now());
        metadata.setLastModifiedAt(Instant.now());
        metadata.setTags(buildTags(tags, metadata));

        return FileInfoDto.from(repository.save(metadata));
    }

    public byte[] download(UUID id) throws IOException {
        var metadata  = findOrThrow(id);
        var encrypted = fileStorage.retrieve(metadata.getStoredPath());
        return encryptionService.decrypt(encrypted);
    }

    @Transactional
    public void delete(UUID id) throws IOException {
        var metadata = findOrThrow(id);
        fileStorage.delete(metadata.getStoredPath());
        repository.delete(metadata);
    }

    public PagedResponseDto<FileInfoDto> listFiles(Pageable pageable) {
        return PagedResponseDto.from(repository.findAll(pageable).map(FileInfoDto::from));
    }

    public PagedResponseDto<FileInfoDto> search(SearchCriteria criteria, Pageable pageable) {
        var spec = FileSpecification.fromCriteria(criteria);
        return PagedResponseDto.from(repository.findAll(spec, pageable).map(FileInfoDto::from));
    }

    public FileInfoDto getMetadata(UUID id) {
        return FileInfoDto.from(findOrThrow(id));
    }

    @Transactional
    public FileInfoDto updateTags(UUID id, Map<String, String> tags) {
        var metadata = findOrThrow(id);
        metadata.getTags().clear();
        metadata.getTags().addAll(buildTags(tags, metadata));
        metadata.setLastModifiedAt(Instant.now());
        return FileInfoDto.from(repository.save(metadata));
    }

    private FileMetadata findOrThrow(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new FileNotFoundException("File not found: " + id));
    }

    private List<FileTag> buildTags(Map<String, String> tags, FileMetadata owner) {
        if (tags == null || tags.isEmpty()) return new ArrayList<>();
        return tags.entrySet().stream().map(e -> {
            var tag = new FileTag();
            tag.setId(uuidGenerator.generate());
            tag.setFile(owner);
            tag.setName(e.getKey());
            tag.setValue(e.getValue());
            return tag;
        }).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private String sha256Hex(byte[] data) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String sanitizeFilename(String name) {
        if (name == null) return "unnamed";
        return Path.of(name).getFileName().toString();
    }

    private String normalizeDirectory(String dir) {
        if (dir == null || dir.isBlank()) return null;
        var normalized = dir.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }
}

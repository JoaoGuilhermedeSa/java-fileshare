package com.fileshare.controller;

import com.fileshare.dto.FileInfoDto;
import com.fileshare.dto.PagedResponseDto;
import com.fileshare.dto.SearchCriteria;
import com.fileshare.service.FileService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FileInfoDto> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String directory,
            @RequestParam(required = false) Map<String, String> allParams) throws IOException {

        var tags = allParams == null ? Map.<String, String>of()
                : allParams.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("tag."))
                    .collect(java.util.stream.Collectors.toMap(
                            e -> e.getKey().substring(4),
                            Map.Entry::getValue));

        var info = fileService.upload(file, directory, tags);
        return ResponseEntity.status(201).body(info);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id) {
        var info = fileService.getMetadata(id);
        StreamingResponseBody body = out -> fileService.download(id, out);

        var headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(info.originalName())
                .build());
        headers.setContentType(MediaType.parseMediaType(info.contentType()));
        headers.setContentLength(info.sizeBytes());

        return ResponseEntity.ok().headers(headers).body(body);
    }

    @GetMapping("/{id}")
    public FileInfoDto getMetadata(@PathVariable UUID id) {
        return fileService.getMetadata(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) throws IOException {
        fileService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public PagedResponseDto<FileInfoDto> listFiles(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(page, Math.min(size, 50), Sort.by(Sort.Direction.DESC, "uploadedAt"));
        return fileService.listFiles(pageable);
    }

    @GetMapping("/search")
    public PagedResponseDto<FileInfoDto> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String directory,
            @RequestParam(required = false) Map<String, String> allParams,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        var tags = allParams == null ? Map.<String, String>of()
                : allParams.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("tag."))
                    .collect(java.util.stream.Collectors.toMap(
                            e -> e.getKey().substring(4),
                            Map.Entry::getValue));

        var criteria = new SearchCriteria(
                q,
                directory,
                tags,
                from != null ? Instant.parse(from) : null,
                to   != null ? Instant.parse(to)   : null
        );

        var pageable = PageRequest.of(page, Math.min(size, 50), Sort.by(Sort.Direction.DESC, "uploadedAt"));
        return fileService.search(criteria, pageable);
    }

    @PutMapping("/{id}/tags")
    public FileInfoDto updateTags(
            @PathVariable UUID id,
            @RequestBody Map<String, String> tags) {
        return fileService.updateTags(id, tags);
    }
}

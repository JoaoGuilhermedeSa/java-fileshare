package com.fileshare.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "file_tags")
public class FileTag {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id", nullable = false)
    private FileMetadata file;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "value", nullable = false, length = 512)
    private String value;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public FileMetadata getFile() { return file; }
    public void setFile(FileMetadata file) { this.file = file; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}

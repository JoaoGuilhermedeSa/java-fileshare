package com.fileshare.service.storage;

import com.fileshare.config.StorageProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class LocalFileStorage implements FileStorage {

    private final Path rootDir;

    public LocalFileStorage(StorageProperties props) throws IOException {
        this.rootDir = Path.of(props.rootDir());
        Files.createDirectories(rootDir);
    }

    @Override
    public String store(UUID fileId, byte[] data) throws IOException {
        var relativePath = toRelativePath(fileId);
        var absolutePath = rootDir.resolve(relativePath);
        Files.createDirectories(absolutePath.getParent());
        Files.write(absolutePath, data);
        return relativePath.toString();
    }

    @Override
    public byte[] retrieve(String storedPath) throws IOException {
        return Files.readAllBytes(rootDir.resolve(storedPath));
    }

    @Override
    public void delete(String storedPath) throws IOException {
        Files.deleteIfExists(rootDir.resolve(storedPath));
    }

    private static Path toRelativePath(UUID fileId) {
        var id = fileId.toString();
        return Path.of(id.substring(0, 2), id + ".enc");
    }
}

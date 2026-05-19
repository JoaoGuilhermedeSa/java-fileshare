package com.fileshare.service.storage;

import com.fileshare.config.StorageProperties;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
    public String store(UUID fileId, StreamWriter writer) throws IOException {
        var relativePath = toRelativePath(fileId);
        var absolutePath = rootDir.resolve(relativePath);
        Files.createDirectories(absolutePath.getParent());
        try (var out = new BufferedOutputStream(Files.newOutputStream(absolutePath))) {
            writer.writeTo(out);
        } catch (IOException e) {
            safeDelete(absolutePath);
            throw e;
        } catch (RuntimeException e) {
            safeDelete(absolutePath);
            throw e;
        }
        return relativePath.toString();
    }

    @Override
    public InputStream retrieve(String storedPath) throws IOException {
        return new BufferedInputStream(Files.newInputStream(rootDir.resolve(storedPath)));
    }

    @Override
    public void delete(String storedPath) throws IOException {
        Files.deleteIfExists(rootDir.resolve(storedPath));
    }

    private static Path toRelativePath(UUID fileId) {
        var id = fileId.toString();
        return Path.of(id.substring(0, 2), id + ".enc");
    }

    private void safeDelete(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) {}
    }
}

package com.fileshare.service.storage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public interface FileStorage {

    @FunctionalInterface
    interface StreamWriter {
        void writeTo(OutputStream out) throws IOException;
    }

    String store(UUID fileId, StreamWriter writer) throws IOException;

    InputStream retrieve(String storedPath) throws IOException;

    void delete(String storedPath) throws IOException;
}

package com.fileshare.service.storage;

import java.io.IOException;
import java.util.UUID;

public interface FileStorage {

    String store(UUID fileId, byte[] data) throws IOException;

    byte[] retrieve(String storedPath) throws IOException;

    void delete(String storedPath) throws IOException;
}

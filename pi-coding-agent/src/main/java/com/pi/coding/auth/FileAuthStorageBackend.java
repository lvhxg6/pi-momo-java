package com.pi.coding.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pi.ai.core.util.PiAiJson;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * File-based implementation of {@link AuthStorageBackend}.
 * 
 * <p>Credentials are stored as JSON in a file. Uses file locking to prevent
 * concurrent access from multiple processes.
 */
public class FileAuthStorageBackend implements AuthStorageBackend {
    
    private static final ObjectMapper MAPPER = PiAiJson.MAPPER;
    private static final TypeReference<Map<String, AuthCredential>> CREDENTIALS_TYPE = 
        new TypeReference<>() {};
    
    private final Path filePath;
    
    /**
     * Creates a file-based backend.
     * 
     * @param filePath path to the credentials file
     */
    public FileAuthStorageBackend(String filePath) {
        this.filePath = Path.of(Objects.requireNonNull(filePath, "filePath must not be null"));
    }
    
    /**
     * Creates a file-based backend.
     * 
     * @param filePath path to the credentials file
     */
    public FileAuthStorageBackend(Path filePath) {
        this.filePath = Objects.requireNonNull(filePath, "filePath must not be null");
    }
    
    @Override
    public Map<String, AuthCredential> load() {
        if (!Files.exists(filePath)) {
            return new HashMap<>();
        }
        
        try {
            return withFileLock(false, () -> {
                String content = Files.readString(filePath);
                if (content.isBlank()) {
                    return new HashMap<>();
                }
                Map<String, AuthCredential> loaded = MAPPER.readValue(content, CREDENTIALS_TYPE);
                return loaded != null ? new HashMap<>(loaded) : new HashMap<>();
            });
        } catch (IOException e) {
            // If file is corrupted or unreadable, return empty map
            return new HashMap<>();
        }
    }
    
    @Override
    public void save(Map<String, AuthCredential> credentials) {
        try {
            // Ensure parent directories exist
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            
            withFileLock(true, () -> {
                String json = MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(credentials);
                Files.writeString(filePath, json);
                return null;
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to save credentials to " + filePath, e);
        }
    }
    
    /**
     * Executes an operation with a file lock.
     * 
     * @param exclusive true for write lock, false for read lock
     * @param operation the operation to execute
     * @return the operation result
     */
    private <T> T withFileLock(boolean exclusive, IOSupplier<T> operation) throws IOException {
        // Ensure file exists for locking
        if (!Files.exists(filePath)) {
            Path parent = filePath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(filePath, "{}");
        }
        
        try (RandomAccessFile raf = new RandomAccessFile(filePath.toFile(), exclusive ? "rw" : "r");
             FileChannel channel = raf.getChannel();
             FileLock lock = channel.lock(0, Long.MAX_VALUE, !exclusive)) {
            return operation.get();
        }
    }
    
    @FunctionalInterface
    private interface IOSupplier<T> {
        T get() throws IOException;
    }
}

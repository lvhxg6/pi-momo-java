package com.pi.coding.compaction;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks file operations during a session for compaction summaries.
 *
 * <p>Tracks files that were read, written, and edited. Used to append
 * file operation lists to compaction summaries.
 *
 * <p><b>Validates: Requirement 3.12</b>
 */
public final class FileOperations {

    private final Set<String> read = new HashSet<>();
    private final Set<String> written = new HashSet<>();
    private final Set<String> edited = new HashSet<>();

    /**
     * Create empty file operations tracker.
     */
    public FileOperations() {
    }

    /**
     * Add a file that was read.
     */
    public void addRead(String path) {
        if (path != null && !path.isEmpty()) {
            read.add(path);
        }
    }

    /**
     * Add a file that was written.
     */
    public void addWritten(String path) {
        if (path != null && !path.isEmpty()) {
            written.add(path);
        }
    }

    /**
     * Add a file that was edited.
     */
    public void addEdited(String path) {
        if (path != null && !path.isEmpty()) {
            edited.add(path);
        }
    }

    /**
     * Get files that were read.
     */
    public Set<String> getRead() {
        return Collections.unmodifiableSet(read);
    }

    /**
     * Get files that were written.
     */
    public Set<String> getWritten() {
        return Collections.unmodifiableSet(written);
    }

    /**
     * Get files that were edited.
     */
    public Set<String> getEdited() {
        return Collections.unmodifiableSet(edited);
    }

    /**
     * Merge another FileOperations into this one.
     */
    public void merge(FileOperations other) {
        if (other != null) {
            read.addAll(other.read);
            written.addAll(other.written);
            edited.addAll(other.edited);
        }
    }

    /**
     * Check if any operations were tracked.
     */
    public boolean isEmpty() {
        return read.isEmpty() && written.isEmpty() && edited.isEmpty();
    }
}

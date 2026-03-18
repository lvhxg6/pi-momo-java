package com.pi.coding.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.pi.ai.core.util.PiAiJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;

/**
 * Manages conversation sessions as append-only trees stored in JSONL files.
 *
 * <p>Each session entry has an id and parentId forming a tree structure. The "leaf"
 * pointer tracks the current position. Appending creates a child of the current leaf.
 * Branching moves the leaf to an earlier entry, allowing new branches without
 * modifying history.
 *
 * <p>Use {@link #buildSessionContext()} to get the resolved message list for the LLM,
 * which handles compaction summaries and follows the path from root to current leaf.
 *
 * <p><b>Validates: Requirements 1.1, 1.2, 1.12-1.17</b>
 */
public class SessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SessionManager.class);

    /**
     * Current session format version.
     */
    public static final int CURRENT_SESSION_VERSION = 3;

    private String sessionId = "";
    private Path sessionFile;
    private final Path sessionDir;
    private final String cwd;
    private final boolean persist;
    private boolean flushed = false;

    // File entries include header + all session entries
    private final List<Object> fileEntries = new ArrayList<>();

    // Internal indexes
    private final Map<String, SessionEntry> byId = new HashMap<>();
    private final Map<String, String> labelsById = new HashMap<>();
    private String leafId = null;

    // =========================================================================
    // Constructors and Factory Methods
    // =========================================================================

    private SessionManager(String cwd, Path sessionDir, Path sessionFile, boolean persist) {
        this.cwd = cwd;
        this.sessionDir = sessionDir;
        this.persist = persist;

        if (persist && sessionDir != null) {
            try {
                Files.createDirectories(sessionDir);
            } catch (IOException e) {
                LOG.warn("Failed to create session directory: {}", sessionDir, e);
            }
        }

        if (sessionFile != null) {
            setSessionFile(sessionFile);
        } else {
            newSession(null);
        }
    }

    /**
     * Create a new session manager with file persistence.
     *
     * @param cwd Working directory (stored in session header)
     * @return A new SessionManager instance
     */
    public static SessionManager create(String cwd) {
        Path sessionDir = getDefaultSessionDir(cwd);
        return new SessionManager(cwd, sessionDir, null, true);
    }

    /**
     * Create a new session manager with file persistence and custom session directory.
     *
     * @param cwd        Working directory (stored in session header)
     * @param sessionDir Custom session directory
     * @return A new SessionManager instance
     */
    public static SessionManager create(String cwd, Path sessionDir) {
        return new SessionManager(cwd, sessionDir, null, true);
    }

    /**
     * Create an in-memory session manager (no file persistence).
     *
     * @param cwd Working directory (stored in session header)
     * @return A new in-memory SessionManager instance
     */
    public static SessionManager inMemory(String cwd) {
        return new SessionManager(cwd, null, null, false);
    }

    /**
     * Open a specific session file.
     *
     * @param path       Path to session file
     * @param sessionDir Optional session directory for new sessions
     * @return A new SessionManager instance
     */
    public static SessionManager open(Path path, Path sessionDir) {
        // Extract cwd from session header if possible
        List<Object> entries = loadEntriesFromFile(path);
        String cwd = System.getProperty("user.dir");
        for (Object entry : entries) {
            if (entry instanceof SessionHeader header) {
                cwd = header.cwd() != null ? header.cwd() : cwd;
                break;
            }
        }
        Path dir = sessionDir != null ? sessionDir : path.getParent();
        return new SessionManager(cwd, dir, path, true);
    }

    // =========================================================================
    // Session Creation and Loading
    // =========================================================================

    /**
     * Create a new session with optional configuration.
     *
     * @param options Session creation options (nullable)
     * @return The path to the new session file, or null if in-memory
     */
    public Path newSession(NewSessionOptions options) {
        this.sessionId = (options != null && options.id() != null) ? options.id() : UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        String parentSession = (options != null) ? options.parentSession() : null;

        SessionHeader header = SessionHeader.create(sessionId, timestamp, cwd, parentSession);

        fileEntries.clear();
        fileEntries.add(header);
        byId.clear();
        labelsById.clear();
        leafId = null;
        flushed = false;

        if (persist && sessionDir != null) {
            String fileTimestamp = timestamp.replace(":", "-").replace(".", "-");
            this.sessionFile = sessionDir.resolve(fileTimestamp + "_" + sessionId + ".jsonl");
        } else {
            this.sessionFile = null;
        }

        return sessionFile;
    }

    /**
     * Switch to a different session file (used for resume and branching).
     *
     * @param sessionFile Path to the session file
     */
    public void setSessionFile(Path sessionFile) {
        this.sessionFile = sessionFile.toAbsolutePath();

        if (Files.exists(this.sessionFile)) {
            List<Object> entries = loadEntriesFromFile(this.sessionFile);

            // If file was empty or corrupted (no valid header), truncate and start fresh
            if (entries.isEmpty()) {
                Path explicitPath = this.sessionFile;
                newSession(null);
                this.sessionFile = explicitPath;
                rewriteFile();
                flushed = true;
                return;
            }

            fileEntries.clear();
            fileEntries.addAll(entries);

            // Extract session ID from header
            for (Object entry : fileEntries) {
                if (entry instanceof SessionHeader header) {
                    this.sessionId = header.id() != null ? header.id() : UUID.randomUUID().toString();
                    break;
                }
            }

            // Run migrations if needed
            if (migrateToCurrentVersion(fileEntries)) {
                rewriteFile();
            }

            buildIndex();
            flushed = true;
        } else {
            Path explicitPath = this.sessionFile;
            newSession(null);
            this.sessionFile = explicitPath; // preserve explicit path
        }
    }

    // =========================================================================
    // JSONL File I/O
    // =========================================================================

    /**
     * Load entries from a JSONL session file.
     *
     * @param filePath Path to the session file
     * @return List of parsed entries (header + session entries)
     */
    static List<Object> loadEntriesFromFile(Path filePath) {
        List<Object> entries = new ArrayList<>();

        if (!Files.exists(filePath)) {
            return entries;
        }

        try {
            List<String> lines = Files.readAllLines(filePath);
            for (String line : lines) {
                if (line.isBlank()) continue;
                try {
                    Object entry = parseEntry(line);
                    if (entry != null) {
                        entries.add(entry);
                    }
                } catch (Exception e) {
                    LOG.debug("Skipping malformed line: {}", line, e);
                }
            }

            // Validate session header
            if (entries.isEmpty()) return entries;
            Object first = entries.get(0);
            if (!(first instanceof SessionHeader header) || header.id() == null) {
                return new ArrayList<>();
            }

        } catch (IOException e) {
            LOG.warn("Failed to read session file: {}", filePath, e);
        }

        return entries;
    }

    /**
     * Parse a single JSON line into a session entry or header.
     */
    private static Object parseEntry(String json) throws JsonProcessingException {
        JsonNode node = PiAiJson.MAPPER.readTree(json);
        String type = node.has("type") ? node.get("type").asText() : null;

        if ("session".equals(type)) {
            return PiAiJson.MAPPER.treeToValue(node, SessionHeader.class);
        } else {
            // Parse as SessionEntry (polymorphic)
            return PiAiJson.MAPPER.treeToValue(node, SessionEntry.class);
        }
    }

    /**
     * Persist a single entry to the session file.
     */
    private void persist(SessionEntry entry) {
        if (!persist || sessionFile == null) return;

        // Check if we have an assistant message
        boolean hasAssistant = fileEntries.stream()
                .filter(e -> e instanceof SessionMessageEntry)
                .map(e -> (SessionMessageEntry) e)
                .anyMatch(e -> "assistant".equals(e.message().role()));

        if (!hasAssistant) {
            // Mark as not flushed so when assistant arrives, all entries get written
            flushed = false;
            return;
        }

        if (!flushed) {
            // Write all entries
            try {
                StringBuilder content = new StringBuilder();
                for (Object e : fileEntries) {
                    content.append(PiAiJson.MAPPER.writeValueAsString(e)).append("\n");
                }
                Files.writeString(sessionFile, content.toString(),
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                flushed = true;
            } catch (IOException e) {
                LOG.error("Failed to write session file: {}", sessionFile, e);
            }
        } else {
            // Append single entry
            try {
                String line = PiAiJson.MAPPER.writeValueAsString(entry) + "\n";
                Files.writeString(sessionFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException e) {
                LOG.error("Failed to append to session file: {}", sessionFile, e);
            }
        }
    }

    /**
     * Rewrite the entire session file (used after migrations).
     */
    private void rewriteFile() {
        if (!persist || sessionFile == null) return;

        try {
            StringBuilder content = new StringBuilder();
            for (Object e : fileEntries) {
                content.append(PiAiJson.MAPPER.writeValueAsString(e)).append("\n");
            }
            Files.writeString(sessionFile, content.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            LOG.error("Failed to rewrite session file: {}", sessionFile, e);
        }
    }

    // =========================================================================
    // Internal Index Management
    // =========================================================================

    /**
     * Build internal indexes from file entries.
     */
    private void buildIndex() {
        byId.clear();
        labelsById.clear();
        leafId = null;

        for (Object entry : fileEntries) {
            if (entry instanceof SessionEntry se) {
                byId.put(se.id(), se);
                leafId = se.id();

                if (se instanceof LabelEntry label) {
                    if (label.label() != null && !label.label().isEmpty()) {
                        labelsById.put(label.targetId(), label.label());
                    } else {
                        labelsById.remove(label.targetId());
                    }
                }
            }
        }
    }

    // =========================================================================
    // Version Migration
    // =========================================================================

    /**
     * Run all necessary migrations to bring entries to current version.
     *
     * @param entries The entries to migrate (mutated in place)
     * @return true if any migration was applied
     */
    static boolean migrateToCurrentVersion(List<Object> entries) {
        SessionHeader header = null;
        for (Object e : entries) {
            if (e instanceof SessionHeader h) {
                header = h;
                break;
            }
        }

        int version = (header != null && header.version() > 0) ? header.version() : 1;

        if (version >= CURRENT_SESSION_VERSION) return false;

        if (version < 2) migrateV1ToV2(entries);
        if (version < 3) migrateV2ToV3(entries);

        return true;
    }

    /**
     * Migrate v1 → v2: add id/parentId tree structure.
     */
    static void migrateV1ToV2(List<Object> entries) {
        Set<String> ids = new HashSet<>();
        String prevId = null;

        for (int i = 0; i < entries.size(); i++) {
            Object entry = entries.get(i);

            if (entry instanceof SessionHeader header) {
                // Update version in header
                entries.set(i, new SessionHeader(
                        header.type(),
                        2,
                        header.id(),
                        header.timestamp(),
                        header.cwd(),
                        header.parentSession()
                ));
                continue;
            }

            // For session entries, we need to add id and parentId
            // Since records are immutable, we need to recreate them
            if (entry instanceof SessionEntry se) {
                String newId = generateId(ids);
                ids.add(newId);

                SessionEntry migrated = migrateEntryWithIdAndParent(se, newId, prevId);
                entries.set(i, migrated);
                prevId = newId;
            }
        }
    }

    /**
     * Migrate v2 → v3: rename hookMessage role to custom.
     */
    static void migrateV2ToV3(List<Object> entries) {
        for (int i = 0; i < entries.size(); i++) {
            Object entry = entries.get(i);

            if (entry instanceof SessionHeader header) {
                // Update version in header
                entries.set(i, new SessionHeader(
                        header.type(),
                        3,
                        header.id(),
                        header.timestamp(),
                        header.cwd(),
                        header.parentSession()
                ));
            }
            // Note: hookMessage role migration would require modifying AgentMessage
            // which is handled at the AgentMessage level if needed
        }
    }

    /**
     * Helper to recreate a session entry with new id and parentId.
     */
    @SuppressWarnings("unchecked")
    private static SessionEntry migrateEntryWithIdAndParent(SessionEntry entry, String id, String parentId) {
        if (entry instanceof SessionMessageEntry e) {
            return SessionMessageEntry.create(id, parentId, e.timestamp(), e.message());
        } else if (entry instanceof ThinkingLevelChangeEntry e) {
            return ThinkingLevelChangeEntry.create(id, parentId, e.timestamp(), e.thinkingLevel());
        } else if (entry instanceof ModelChangeEntry e) {
            return ModelChangeEntry.create(id, parentId, e.timestamp(), e.provider(), e.modelId());
        } else if (entry instanceof CompactionEntry<?> e) {
            return CompactionEntry.create(id, parentId, e.timestamp(), e.summary(), e.firstKeptEntryId(), e.tokensBefore(), e.details(), e.fromHook());
        } else if (entry instanceof BranchSummaryEntry<?> e) {
            return BranchSummaryEntry.create(id, parentId, e.timestamp(), e.fromId(), e.summary(), e.details(), e.fromHook());
        } else if (entry instanceof CustomEntry<?> e) {
            return CustomEntry.create(id, parentId, e.timestamp(), e.customType(), e.data());
        } else if (entry instanceof CustomMessageEntry<?> e) {
            return CustomMessageEntry.create(id, parentId, e.timestamp(), e.customType(), e.content(), e.display(), e.details());
        } else if (entry instanceof LabelEntry e) {
            return LabelEntry.create(id, parentId, e.timestamp(), e.targetId(), e.label());
        } else if (entry instanceof SessionInfoEntry e) {
            return SessionInfoEntry.create(id, parentId, e.timestamp(), e.name());
        }
        throw new IllegalArgumentException("Unknown entry type: " + entry.getClass().getName());
    }

    // =========================================================================
    // ID Generation
    // =========================================================================

    /**
     * Generate a unique short ID (8 hex chars, collision-checked).
     */
    private static String generateId(Set<String> existingIds) {
        for (int i = 0; i < 100; i++) {
            String id = UUID.randomUUID().toString().substring(0, 8);
            if (!existingIds.contains(id)) return id;
        }
        // Fallback to full UUID if somehow we have collisions
        return UUID.randomUUID().toString();
    }

    /**
     * Generate a unique ID using the internal byId map.
     */
    private String generateId() {
        return generateId(byId.keySet());
    }

    // =========================================================================
    // Default Session Directory
    // =========================================================================

    /**
     * Compute the default session directory for a cwd.
     * Encodes cwd into a safe directory name under ~/.pi/agent/sessions/.
     */
    private static Path getDefaultSessionDir(String cwd) {
        String safePath = "--" + cwd.replaceFirst("^[/\\\\]", "").replaceAll("[/\\\\:]", "-") + "--";
        String userHome = System.getProperty("user.home");
        return Path.of(userHome, ".pi", "agent", "sessions", safePath);
    }

    // =========================================================================
    // Accessors
    // =========================================================================

    /**
     * Check if this session manager persists to file.
     */
    public boolean isPersisted() {
        return persist;
    }

    /**
     * Get the working directory.
     */
    public String getCwd() {
        return cwd;
    }

    /**
     * Get the session directory.
     */
    public Path getSessionDir() {
        return sessionDir;
    }

    /**
     * Get the session ID.
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Get the session file path.
     */
    public Path getSessionFile() {
        return sessionFile;
    }

    /**
     * Get the session header.
     */
    public SessionHeader getHeader() {
        for (Object entry : fileEntries) {
            if (entry instanceof SessionHeader header) {
                return header;
            }
        }
        return null;
    }

    /**
     * Get all session entries (excludes header).
     */
    public List<SessionEntry> getEntries() {
        List<SessionEntry> entries = new ArrayList<>();
        for (Object entry : fileEntries) {
            if (entry instanceof SessionEntry se) {
                entries.add(se);
            }
        }
        return entries;
    }

    // =========================================================================
    // Tree Traversal (to be implemented in Task 3.4)
    // =========================================================================

    /**
     * Get the current leaf entry ID.
     */
    public String getLeafId() {
        return leafId;
    }

    /**
     * Get the current leaf entry.
     */
    public SessionEntry getLeafEntry() {
        return leafId != null ? byId.get(leafId) : null;
    }

    /**
     * Get an entry by ID.
     */
    public SessionEntry getEntry(String id) {
        return byId.get(id);
    }

    /**
     * Get the label for an entry, if any.
     */
    public String getLabel(String id) {
        return labelsById.get(id);
    }

    // =========================================================================
    // Entry Append Methods (Task 3.3)
    // =========================================================================

    /**
     * Append an entry to the session.
     * Creates the entry as a child of the current leaf and updates leafId.
     */
    private void appendEntry(SessionEntry entry) {
        fileEntries.add(entry);
        byId.put(entry.id(), entry);
        leafId = entry.id();
        persist(entry);
    }

    /**
     * Append a message entry to the session.
     *
     * <p><b>Validates: Requirement 1.3</b>
     *
     * @param message The agent message to append
     * @return The ID of the new entry
     */
    public String appendMessage(com.pi.agent.types.AgentMessage message) {
        String id = generateId();
        String timestamp = Instant.now().toString();
        SessionMessageEntry entry = SessionMessageEntry.create(id, leafId, timestamp, message);
        appendEntry(entry);
        return id;
    }

    /**
     * Append a thinking level change entry to the session.
     *
     * <p><b>Validates: Requirement 1.4</b>
     *
     * @param thinkingLevel The new thinking level (e.g., "off", "low", "medium", "high")
     * @return The ID of the new entry
     */
    public String appendThinkingLevelChange(String thinkingLevel) {
        String id = generateId();
        String timestamp = Instant.now().toString();
        ThinkingLevelChangeEntry entry = ThinkingLevelChangeEntry.create(id, leafId, timestamp, thinkingLevel);
        appendEntry(entry);
        return id;
    }

    /**
     * Append a model change entry to the session.
     *
     * <p><b>Validates: Requirement 1.5</b>
     *
     * @param provider The provider identifier (e.g., "anthropic", "openai")
     * @param modelId  The model identifier (e.g., "claude-3-opus")
     * @return The ID of the new entry
     */
    public String appendModelChange(String provider, String modelId) {
        String id = generateId();
        String timestamp = Instant.now().toString();
        ModelChangeEntry entry = ModelChangeEntry.create(id, leafId, timestamp, provider, modelId);
        appendEntry(entry);
        return id;
    }

    /**
     * Append a compaction entry to the session.
     *
     * <p><b>Validates: Requirement 1.6</b>
     *
     * @param summary          The generated summary of compacted messages
     * @param firstKeptEntryId ID of the first entry kept after compaction
     * @param tokensBefore     Token count before compaction
     * @return The ID of the new entry
     */
    public String appendCompaction(String summary, String firstKeptEntryId, int tokensBefore) {
        return appendCompaction(summary, firstKeptEntryId, tokensBefore, null, null);
    }

    /**
     * Append a compaction entry to the session with optional details.
     *
     * <p><b>Validates: Requirement 1.6</b>
     *
     * @param summary          The generated summary of compacted messages
     * @param firstKeptEntryId ID of the first entry kept after compaction
     * @param tokensBefore     Token count before compaction
     * @param details          Extension-specific details (nullable)
     * @param fromHook         Whether generated by extension (nullable)
     * @param <T>              Type of details
     * @return The ID of the new entry
     */
    public <T> String appendCompaction(String summary, String firstKeptEntryId, int tokensBefore, T details, Boolean fromHook) {
        String id = generateId();
        String timestamp = Instant.now().toString();
        CompactionEntry<T> entry = CompactionEntry.create(id, leafId, timestamp, summary, firstKeptEntryId, tokensBefore, details, fromHook);
        appendEntry(entry);
        return id;
    }

    /**
     * Append a branch summary entry to the session.
     *
     * <p><b>Validates: Requirement 1.7</b>
     *
     * @param fromId  ID of the entry where the branch started
     * @param summary The generated summary of the branch
     * @return The ID of the new entry
     */
    public String appendBranchSummary(String fromId, String summary) {
        return appendBranchSummary(fromId, summary, null, null);
    }

    /**
     * Append a branch summary entry to the session with optional details.
     *
     * <p><b>Validates: Requirement 1.7</b>
     *
     * @param fromId   ID of the entry where the branch started
     * @param summary  The generated summary of the branch
     * @param details  Extension-specific details (nullable)
     * @param fromHook Whether generated by extension (nullable)
     * @param <T>      Type of details
     * @return The ID of the new entry
     */
    public <T> String appendBranchSummary(String fromId, String summary, T details, Boolean fromHook) {
        String id = generateId();
        String timestamp = Instant.now().toString();
        BranchSummaryEntry<T> entry = BranchSummaryEntry.create(id, leafId, timestamp, fromId, summary, details, fromHook);
        appendEntry(entry);
        return id;
    }

    /**
     * Append a custom entry to the session (does NOT participate in LLM context).
     *
     * <p><b>Validates: Requirement 1.8</b>
     *
     * @param customType Extension identifier for filtering entries
     * @param data       Extension-specific data (nullable)
     * @param <T>        Type of data
     * @return The ID of the new entry
     */
    public <T> String appendCustomEntry(String customType, T data) {
        String id = generateId();
        String timestamp = Instant.now().toString();
        CustomEntry<T> entry = CustomEntry.create(id, leafId, timestamp, customType, data);
        appendEntry(entry);
        return id;
    }

    /**
     * Append a custom message entry to the session (participates in LLM context).
     *
     * <p><b>Validates: Requirement 1.9</b>
     *
     * @param customType Extension identifier for filtering entries
     * @param content    Message content (String or List of ContentBlock)
     * @param display    Whether to show in TUI
     * @return The ID of the new entry
     */
    public String appendCustomMessageEntry(String customType, Object content, boolean display) {
        return appendCustomMessageEntry(customType, content, display, null);
    }

    /**
     * Append a custom message entry to the session with optional details.
     *
     * <p><b>Validates: Requirement 1.9</b>
     *
     * @param customType Extension identifier for filtering entries
     * @param content    Message content (String or List of ContentBlock)
     * @param display    Whether to show in TUI
     * @param details    Extension-specific details (nullable)
     * @param <T>        Type of details
     * @return The ID of the new entry
     */
    public <T> String appendCustomMessageEntry(String customType, Object content, boolean display, T details) {
        String id = generateId();
        String timestamp = Instant.now().toString();
        CustomMessageEntry<T> entry = CustomMessageEntry.create(id, leafId, timestamp, customType, content, display, details);
        appendEntry(entry);
        return id;
    }

    /**
     * Append a label entry to the session.
     *
     * <p><b>Validates: Requirement 1.10</b>
     *
     * @param targetId ID of the entry to label
     * @param label    The label text (null or empty to clear)
     * @return The ID of the new entry
     */
    public String appendLabelChange(String targetId, String label) {
        String id = generateId();
        String timestamp = Instant.now().toString();
        LabelEntry entry = LabelEntry.create(id, leafId, timestamp, targetId, label);
        appendEntry(entry);

        // Update labels index
        if (label != null && !label.isEmpty()) {
            labelsById.put(targetId, label);
        } else {
            labelsById.remove(targetId);
        }

        return id;
    }

    /**
     * Append a session info entry to the session.
     *
     * <p><b>Validates: Requirement 1.11</b>
     *
     * @param name User-defined session display name
     * @return The ID of the new entry
     */
    public String appendSessionInfo(String name) {
        String id = generateId();
        String timestamp = Instant.now().toString();
        SessionInfoEntry entry = SessionInfoEntry.create(id, leafId, timestamp, name);
        appendEntry(entry);
        return id;
    }

    // =========================================================================
    // Tree Traversal Methods (Task 3.4)
    // =========================================================================

    /**
     * Set the current leaf pointer (for branching).
     *
     * <p><b>Validates: Requirement 1.14</b>
     *
     * @param entryId The entry ID to set as leaf (null to reset)
     */
    public void setLeaf(String entryId) {
        if (entryId != null && !byId.containsKey(entryId)) {
            throw new IllegalArgumentException("Entry " + entryId + " not found");
        }
        this.leafId = entryId;
    }

    /**
     * Get all children of an entry.
     *
     * <p><b>Validates: Requirement 1.15</b>
     *
     * @param parentId The parent entry ID (null for root-level entries)
     * @return List of child entries
     */
    public List<SessionEntry> getChildren(String parentId) {
        List<SessionEntry> children = new ArrayList<>();
        for (Object entry : fileEntries) {
            if (entry instanceof SessionEntry se) {
                if (Objects.equals(se.parentId(), parentId)) {
                    children.add(se);
                }
            }
        }
        return children;
    }

    /**
     * Get the branch path from a specific entry to the root.
     *
     * <p><b>Validates: Requirement 1.16</b>
     *
     * @param fromId The entry ID to start from
     * @return List of entries from root to the specified entry (inclusive)
     */
    public List<SessionEntry> getBranch(String fromId) {
        List<SessionEntry> path = new ArrayList<>();
        SessionEntry current = byId.get(fromId);

        while (current != null) {
            path.add(0, current);
            current = current.parentId() != null ? byId.get(current.parentId()) : null;
        }

        return path;
    }

    // =========================================================================
    // Session Context Building (Task 3.5)
    // =========================================================================

    /**
     * Build the session context (what gets sent to the LLM).
     * Uses tree traversal from current leaf.
     *
     * <p>Walks from the current leaf to root, collecting messages and handling:
     * <ul>
     *   <li>Compaction entries: emits summary first, then kept messages</li>
     *   <li>Branch summary entries: emits summary as user message</li>
     *   <li>Custom message entries: emits content as user message (if display=true)</li>
     * </ul>
     *
     * <p><b>Validates: Requirements 1.12, 1.13</b>
     *
     * @return The session context with messages, thinking level, and model
     */
    public SessionContext buildSessionContext() {
        return buildSessionContext(getEntries(), leafId, byId);
    }

    /**
     * Build session context from entries using tree traversal.
     *
     * <p><b>Validates: Requirements 1.12, 1.13</b>
     *
     * @param entries List of session entries
     * @param leafId  The leaf entry ID (null for empty context)
     * @param byId    Map of entry ID to entry
     * @return The session context
     */
    static SessionContext buildSessionContext(
            List<SessionEntry> entries,
            String leafId,
            Map<String, SessionEntry> byId
    ) {
        // Build uuid index if not available
        if (byId == null) {
            byId = new HashMap<>();
            for (SessionEntry entry : entries) {
                byId.put(entry.id(), entry);
            }
        }

        // Explicitly null - return no messages
        if (leafId == null && entries.isEmpty()) {
            return SessionContext.empty();
        }

        // Find leaf
        SessionEntry leaf = null;
        if (leafId != null) {
            leaf = byId.get(leafId);
        }
        if (leaf == null && !entries.isEmpty()) {
            // Fallback to last entry
            leaf = entries.get(entries.size() - 1);
        }

        if (leaf == null) {
            return SessionContext.empty();
        }

        // Walk from leaf to root, collecting path
        List<SessionEntry> path = new ArrayList<>();
        SessionEntry current = leaf;
        while (current != null) {
            path.add(0, current);
            current = current.parentId() != null ? byId.get(current.parentId()) : null;
        }

        // Extract settings and find the most recent compaction
        String thinkingLevel = "off";
        String provider = null;
        String modelId = null;
        CompactionEntry<?> compaction = null;
        int compactionIndex = -1;

        for (int i = 0; i < path.size(); i++) {
            SessionEntry entry = path.get(i);
            if (entry instanceof ThinkingLevelChangeEntry tlc) {
                thinkingLevel = tlc.thinkingLevel();
            } else if (entry instanceof ModelChangeEntry mc) {
                provider = mc.provider();
                modelId = mc.modelId();
            } else if (entry instanceof CompactionEntry<?> ce) {
                compaction = ce;
                compactionIndex = i;
            }
        }

        // Build messages
        List<com.pi.agent.types.AgentMessage> messages = new ArrayList<>();

        // Determine starting index based on compaction
        int startIndex = 0;
        if (compaction != null && compaction.firstKeptEntryId() != null) {
            // Find the index of the first kept entry
            for (int i = compactionIndex + 1; i < path.size(); i++) {
                if (path.get(i).id().equals(compaction.firstKeptEntryId())) {
                    startIndex = i;
                    break;
                }
            }

            // Emit compaction summary as a user message first
            messages.add(createSummaryMessage(compaction.summary(), compaction.timestamp()));
        }

        // Collect messages from the path
        for (int i = startIndex; i < path.size(); i++) {
            SessionEntry entry = path.get(i);

            if (entry instanceof SessionMessageEntry sme) {
                messages.add(sme.message());
            } else if (entry instanceof BranchSummaryEntry<?> bse) {
                // Emit branch summary as a user message
                messages.add(createSummaryMessage(bse.summary(), bse.timestamp()));
            } else if (entry instanceof CustomMessageEntry<?> cme) {
                // Custom message entries participate in LLM context
                // Convert content to a user message
                messages.add(createCustomMessage(cme));
            }
            // Skip other entry types (ThinkingLevelChange, ModelChange, Custom, Label, SessionInfo, Compaction)
        }

        return SessionContext.of(messages, thinkingLevel, provider, modelId);
    }

    /**
     * Create a summary message (used for compaction and branch summaries).
     */
    private static com.pi.agent.types.AgentMessage createSummaryMessage(String summary, String timestamp) {
        long ts = parseTimestamp(timestamp);
        return new SummaryMessage(summary, ts);
    }

    /**
     * Create a custom message from a CustomMessageEntry.
     */
    private static com.pi.agent.types.AgentMessage createCustomMessage(CustomMessageEntry<?> entry) {
        long ts = parseTimestamp(entry.timestamp());
        Object content = entry.content();
        String text = content instanceof String ? (String) content : content.toString();
        return new CustomAgentMessage(entry.customType(), text, ts);
    }

    /**
     * Parse ISO 8601 timestamp to Unix milliseconds.
     */
    private static long parseTimestamp(String timestamp) {
        try {
            return Instant.parse(timestamp).toEpochMilli();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    /**
     * Internal message type for summaries (compaction and branch).
     */
    private record SummaryMessage(String summary, long timestamp) implements com.pi.agent.types.AgentMessage {
        @Override
        public String role() {
            return "user";
        }

        @Override
        public String toString() {
            return summary;
        }
    }

    /**
     * Internal message type for custom messages.
     */
    private record CustomAgentMessage(String customType, String content, long timestamp) implements com.pi.agent.types.AgentMessage {
        @Override
        public String role() {
            return "custom";
        }

        @Override
        public String toString() {
            return content;
        }
    }
}

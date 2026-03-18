package com.pi.coding.resource;

import com.pi.coding.util.Frontmatter;
import com.pi.coding.util.FrontmatterResult;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

/**
 * Skills system for loading and managing agent skills.
 * 
 * <p>Skills are specialized instructions stored in SKILL.md files.
 * They are discovered from configured directories and can be invoked
 * by the agent when the task matches the skill's description.
 * 
 * <p>Discovery rules:
 * <ul>
 *   <li>If a directory contains SKILL.md, treat it as a skill root and do not recurse further</li>
 *   <li>Otherwise, load direct .md children in the root</li>
 *   <li>Recurse into subdirectories to find SKILL.md</li>
 *   <li>Respect .gitignore and .ignore files</li>
 * </ul>
 */
public final class Skills {
    
    private static final int MAX_NAME_LENGTH = 64;
    private static final int MAX_DESCRIPTION_LENGTH = 1024;
    private static final String[] IGNORE_FILE_NAMES = {".gitignore", ".ignore", ".fdignore"};
    
    private Skills() {
        // Utility class
    }
    
    /**
     * Load skills from a directory.
     * 
     * @param options Load options
     * @return Load result with skills and diagnostics
     */
    public static LoadSkillsResult loadSkillsFromDir(LoadSkillsFromDirOptions options) {
        List<Skill> skills = new ArrayList<>();
        List<ResourceDiagnostic> diagnostics = new ArrayList<>();
        
        Path dir = Paths.get(options.dir());
        if (!Files.exists(dir)) {
            return new LoadSkillsResult(skills, diagnostics);
        }
        
        IgnoreRules ignoreRules = new IgnoreRules();
        loadIgnoreRules(ignoreRules, dir, dir);
        
        loadSkillsFromDirInternal(dir, options.source(), true, ignoreRules, dir, skills, diagnostics);
        
        return new LoadSkillsResult(skills, diagnostics);
    }
    
    /**
     * Load skills from all configured locations.
     * 
     * @param options Load options
     * @return Load result with skills and diagnostics
     */
    public static LoadSkillsResult loadSkills(LoadSkillsOptions options) {
        Map<String, Skill> skillMap = new LinkedHashMap<>();
        Set<String> realPathSet = new HashSet<>();
        List<ResourceDiagnostic> allDiagnostics = new ArrayList<>();
        List<ResourceDiagnostic> collisionDiagnostics = new ArrayList<>();
        
        // Load from default locations if enabled
        if (options.includeDefaults()) {
            Path userSkillsDir = Paths.get(options.agentDir(), "skills");
            addSkills(loadSkillsFromDir(new LoadSkillsFromDirOptions(
                userSkillsDir.toString(), "user"
            )), skillMap, realPathSet, allDiagnostics, collisionDiagnostics);
            
            Path projectSkillsDir = Paths.get(options.cwd(), ".kiro", "skills");
            addSkills(loadSkillsFromDir(new LoadSkillsFromDirOptions(
                projectSkillsDir.toString(), "project"
            )), skillMap, realPathSet, allDiagnostics, collisionDiagnostics);
        }
        
        // Load from explicit paths
        for (String rawPath : options.skillPaths()) {
            Path resolvedPath = resolveSkillPath(rawPath, options.cwd());
            
            if (!Files.exists(resolvedPath)) {
                allDiagnostics.add(new ResourceDiagnostic(
                    "warning", "skill path does not exist", resolvedPath.toString()
                ));
                continue;
            }
            
            try {
                String source = determineSource(resolvedPath, options);
                
                if (Files.isDirectory(resolvedPath)) {
                    addSkills(loadSkillsFromDir(new LoadSkillsFromDirOptions(
                        resolvedPath.toString(), source
                    )), skillMap, realPathSet, allDiagnostics, collisionDiagnostics);
                } else if (Files.isRegularFile(resolvedPath) && resolvedPath.toString().endsWith(".md")) {
                    LoadSkillFromFileResult result = loadSkillFromFile(resolvedPath, source);
                    if (result.skill() != null) {
                        addSkills(new LoadSkillsResult(
                            List.of(result.skill()), result.diagnostics()
                        ), skillMap, realPathSet, allDiagnostics, collisionDiagnostics);
                    } else {
                        allDiagnostics.addAll(result.diagnostics());
                    }
                } else {
                    allDiagnostics.add(new ResourceDiagnostic(
                        "warning", "skill path is not a markdown file", resolvedPath.toString()
                    ));
                }
            } catch (Exception e) {
                allDiagnostics.add(new ResourceDiagnostic(
                    "warning", "failed to read skill path: " + e.getMessage(), resolvedPath.toString()
                ));
            }
        }
        
        List<ResourceDiagnostic> finalDiagnostics = new ArrayList<>(allDiagnostics);
        finalDiagnostics.addAll(collisionDiagnostics);
        
        return new LoadSkillsResult(new ArrayList<>(skillMap.values()), finalDiagnostics);
    }
    
    /**
     * Format skills for inclusion in a system prompt.
     * Uses XML format per Agent Skills standard.
     * 
     * <p>Skills with disableModelInvocation=true are excluded from the prompt.
     * 
     * @param skills Skills to format
     * @return Formatted XML string
     */
    public static String formatSkillsForPrompt(List<Skill> skills) {
        List<Skill> visibleSkills = skills.stream()
            .filter(s -> !s.disableModelInvocation())
            .toList();
        
        if (visibleSkills.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n\nThe following skills provide specialized instructions for specific tasks.\n");
        sb.append("Use the read tool to load a skill's file when the task matches its description.\n");
        sb.append("When a skill file references a relative path, resolve it against the skill directory ");
        sb.append("(parent of SKILL.md / dirname of the path) and use that absolute path in tool commands.\n");
        sb.append("\n");
        sb.append("<available_skills>\n");
        
        for (Skill skill : visibleSkills) {
            sb.append("  <skill>\n");
            sb.append("    <name>").append(escapeXml(skill.name())).append("</name>\n");
            sb.append("    <description>").append(escapeXml(skill.description())).append("</description>\n");
            sb.append("    <location>").append(escapeXml(skill.filePath())).append("</location>\n");
            sb.append("  </skill>\n");
        }
        
        sb.append("</available_skills>");
        
        return sb.toString();
    }
    
    // Internal methods
    
    private static void loadSkillsFromDirInternal(
        Path dir,
        String source,
        boolean includeRootFiles,
        IgnoreRules ignoreRules,
        Path rootDir,
        List<Skill> skills,
        List<ResourceDiagnostic> diagnostics
    ) {
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            return;
        }
        
        loadIgnoreRules(ignoreRules, dir, rootDir);
        
        try (Stream<Path> entries = Files.list(dir)) {
            List<Path> entryList = entries.toList();
            
            // First pass: look for SKILL.md
            for (Path entry : entryList) {
                if (entry.getFileName().toString().equals("SKILL.md")) {
                    if (!Files.isRegularFile(entry)) {
                        continue;
                    }
                    
                    String relPath = rootDir.relativize(entry).toString().replace('\\', '/');
                    if (ignoreRules.ignores(relPath)) {
                        continue;
                    }
                    
                    LoadSkillFromFileResult result = loadSkillFromFile(entry, source);
                    if (result.skill() != null) {
                        skills.add(result.skill());
                    }
                    diagnostics.addAll(result.diagnostics());
                    return; // Don't recurse further
                }
            }
            
            // Second pass: process subdirectories and root files
            for (Path entry : entryList) {
                String fileName = entry.getFileName().toString();
                
                if (fileName.startsWith(".") || fileName.equals("node_modules")) {
                    continue;
                }
                
                String relPath = rootDir.relativize(entry).toString().replace('\\', '/');
                boolean isDir = Files.isDirectory(entry);
                String ignorePath = isDir ? relPath + "/" : relPath;
                
                if (ignoreRules.ignores(ignorePath)) {
                    continue;
                }
                
                if (isDir) {
                    loadSkillsFromDirInternal(entry, source, false, ignoreRules, rootDir, skills, diagnostics);
                } else if (includeRootFiles && Files.isRegularFile(entry) && fileName.endsWith(".md")) {
                    LoadSkillFromFileResult result = loadSkillFromFile(entry, source);
                    if (result.skill() != null) {
                        skills.add(result.skill());
                    }
                    diagnostics.addAll(result.diagnostics());
                }
            }
        } catch (IOException e) {
            // Silently ignore directory read errors
        }
    }
    
    private static LoadSkillFromFileResult loadSkillFromFile(Path filePath, String source) {
        List<ResourceDiagnostic> diagnostics = new ArrayList<>();
        
        try {
            String rawContent = Files.readString(filePath);
            FrontmatterResult frontmatter = Frontmatter.parseFrontmatter(rawContent);
            
            Path skillDir = filePath.getParent();
            String parentDirName = skillDir.getFileName().toString();
            
            // Get frontmatter fields
            String description = frontmatter.getString("description");
            String name = frontmatter.getString("name");
            if (name == null || name.isEmpty()) {
                name = parentDirName;
            }
            Boolean disableModelInvocation = frontmatter.getBoolean("disable-model-invocation");
            
            // Validate description
            List<String> descErrors = validateDescription(description);
            for (String error : descErrors) {
                diagnostics.add(new ResourceDiagnostic("warning", error, filePath.toString()));
            }
            
            // Validate name
            List<String> nameErrors = validateName(name, parentDirName);
            for (String error : nameErrors) {
                diagnostics.add(new ResourceDiagnostic("warning", error, filePath.toString()));
            }
            
            // Don't load skill if description is missing
            if (description == null || description.trim().isEmpty()) {
                return new LoadSkillFromFileResult(null, diagnostics);
            }
            
            Skill skill = new Skill(
                name,
                description,
                filePath.toString(),
                skillDir.toString(),
                source,
                disableModelInvocation != null && disableModelInvocation
            );
            
            return new LoadSkillFromFileResult(skill, diagnostics);
            
        } catch (Exception e) {
            String message = "failed to parse skill file: " + e.getMessage();
            diagnostics.add(new ResourceDiagnostic("warning", message, filePath.toString()));
            return new LoadSkillFromFileResult(null, diagnostics);
        }
    }
    
    private static List<String> validateName(String name, String parentDirName) {
        List<String> errors = new ArrayList<>();
        
        if (!name.equals(parentDirName)) {
            errors.add("name \"" + name + "\" does not match parent directory \"" + parentDirName + "\"");
        }
        
        if (name.length() > MAX_NAME_LENGTH) {
            errors.add("name exceeds " + MAX_NAME_LENGTH + " characters (" + name.length() + ")");
        }
        
        if (!name.matches("^[a-z0-9-]+$")) {
            errors.add("name contains invalid characters (must be lowercase a-z, 0-9, hyphens only)");
        }
        
        if (name.startsWith("-") || name.endsWith("-")) {
            errors.add("name must not start or end with a hyphen");
        }
        
        if (name.contains("--")) {
            errors.add("name must not contain consecutive hyphens");
        }
        
        return errors;
    }
    
    private static List<String> validateDescription(String description) {
        List<String> errors = new ArrayList<>();
        
        if (description == null || description.trim().isEmpty()) {
            errors.add("description is required");
        } else if (description.length() > MAX_DESCRIPTION_LENGTH) {
            errors.add("description exceeds " + MAX_DESCRIPTION_LENGTH + " characters (" + description.length() + ")");
        }
        
        return errors;
    }
    
    private static void addSkills(
        LoadSkillsResult result,
        Map<String, Skill> skillMap,
        Set<String> realPathSet,
        List<ResourceDiagnostic> allDiagnostics,
        List<ResourceDiagnostic> collisionDiagnostics
    ) {
        allDiagnostics.addAll(result.diagnostics());
        
        for (Skill skill : result.skills()) {
            // Resolve symlinks to detect duplicate files
            String realPath;
            try {
                realPath = Paths.get(skill.filePath()).toRealPath().toString();
            } catch (IOException e) {
                realPath = skill.filePath();
            }
            
            // Skip if we've already loaded this exact file
            if (realPathSet.contains(realPath)) {
                continue;
            }
            
            Skill existing = skillMap.get(skill.name());
            if (existing != null) {
                collisionDiagnostics.add(new ResourceDiagnostic(
                    "collision",
                    "name \"" + skill.name() + "\" collision",
                    skill.filePath(),
                    new ResourceCollision(
                        "skill",
                        skill.name(),
                        existing.filePath(),
                        skill.filePath()
                    )
                ));
            } else {
                skillMap.put(skill.name(), skill);
                realPathSet.add(realPath);
            }
        }
    }
    
    private static Path resolveSkillPath(String rawPath, String cwd) {
        String normalized = normalizePath(rawPath);
        Path path = Paths.get(normalized);
        return path.isAbsolute() ? path : Paths.get(cwd).resolve(normalized);
    }
    
    private static String normalizePath(String input) {
        String trimmed = input.trim();
        String home = System.getProperty("user.home");
        
        if (trimmed.equals("~")) {
            return home;
        }
        if (trimmed.startsWith("~/") || trimmed.startsWith("~\\")) {
            return Paths.get(home, trimmed.substring(2)).toString();
        }
        if (trimmed.startsWith("~")) {
            return Paths.get(home, trimmed.substring(1)).toString();
        }
        
        return trimmed;
    }
    
    private static String determineSource(Path resolvedPath, LoadSkillsOptions options) {
        if (!options.includeDefaults()) {
            Path userSkillsDir = Paths.get(options.agentDir(), "skills");
            Path projectSkillsDir = Paths.get(options.cwd(), ".kiro", "skills");
            
            if (isUnderPath(resolvedPath, userSkillsDir)) {
                return "user";
            }
            if (isUnderPath(resolvedPath, projectSkillsDir)) {
                return "project";
            }
        }
        return "path";
    }
    
    private static boolean isUnderPath(Path target, Path root) {
        try {
            Path normalizedRoot = root.toAbsolutePath().normalize();
            Path normalizedTarget = target.toAbsolutePath().normalize();
            return normalizedTarget.startsWith(normalizedRoot);
        } catch (Exception e) {
            return false;
        }
    }
    
    private static void loadIgnoreRules(IgnoreRules ignoreRules, Path dir, Path rootDir) {
        String relativeDir = rootDir.relativize(dir).toString().replace('\\', '/');
        String prefix = relativeDir.isEmpty() ? "" : relativeDir + "/";
        
        for (String filename : IGNORE_FILE_NAMES) {
            Path ignorePath = dir.resolve(filename);
            if (!Files.exists(ignorePath)) {
                continue;
            }
            
            try {
                String content = Files.readString(ignorePath);
                for (String line : content.split("\\r?\\n")) {
                    String pattern = prefixIgnorePattern(line, prefix);
                    if (pattern != null) {
                        ignoreRules.add(pattern);
                    }
                }
            } catch (IOException e) {
                // Silently ignore read errors
            }
        }
    }
    
    private static String prefixIgnorePattern(String line, String prefix) {
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("#") && !trimmed.startsWith("\\#")) {
            return null;
        }
        
        String pattern = line;
        boolean negated = false;
        
        if (pattern.startsWith("!")) {
            negated = true;
            pattern = pattern.substring(1);
        } else if (pattern.startsWith("\\!")) {
            pattern = pattern.substring(1);
        }
        
        if (pattern.startsWith("/")) {
            pattern = pattern.substring(1);
        }
        
        String prefixed = prefix.isEmpty() ? pattern : prefix + pattern;
        return negated ? "!" + prefixed : prefixed;
    }
    
    private static String escapeXml(String str) {
        return str
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;");
    }
    
    // Helper classes
    
    private static class IgnoreRules {
        private final List<String> patterns = new ArrayList<>();
        
        void add(String pattern) {
            patterns.add(pattern);
        }
        
        boolean ignores(String path) {
            boolean ignored = false;
            
            for (String pattern : patterns) {
                boolean negated = pattern.startsWith("!");
                String actualPattern = negated ? pattern.substring(1) : pattern;
                
                if (matches(path, actualPattern)) {
                    ignored = !negated;
                }
            }
            
            return ignored;
        }
        
        private boolean matches(String path, String pattern) {
            // Simple glob matching
            String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
            
            return path.matches(regex) || path.matches(".*/" + regex);
        }
    }
    
    private record LoadSkillFromFileResult(
        Skill skill,
        List<ResourceDiagnostic> diagnostics
    ) {}
}
